package br.com.conde.lote;

import java.util.ArrayList;
import java.util.List;

/**
 * As MEDIDAS: gravacoes por item e latencia, em tempo simulado. Nunca segundos de relogio.
 *
 * <p>As duas colunas andam ao contrario, e essa tensao e o problema inteiro. Uma estrategia
 * que so olhasse uma delas seria trivial de escrever e inutil.
 *
 * <p>Toda tabela daqui tem um teste do lado que afirma os mesmos numeros.
 */
public final class Medidas {

    private Medidas() {
    }

    public static final int ITENS = 100_000;

    /** O custo fixo de uma gravacao duravel, em tiques. E a unidade das colunas de tempo. */
    public static final long CUSTO_FIXO = 1_000;

    /** O custo por item, cem vezes menor: e dessa razao que o agrupamento vive. */
    public static final long CUSTO_POR_ITEM = 10;

    public static final long SEMENTE = 20_251_012L;

    private static Aparelho aparelho() {
        return new Aparelho(CUSTO_FIXO, CUSTO_POR_ITEM);
    }

    // ───────────────────── 1. a correcao ─────────────────────

    public record LinhaCorrecao(String estrategia, String chegadas, int esperados,
                                int gravados, int perdidos) {
    }

    /**
     * TODO ITEM E GRAVADO UMA VEZ SO, e uma das estrategias falha nisso.
     *
     * <p>A por contagem perde itens, e perde de um jeito que nenhuma medida de desempenho
     * mostraria. A tabela da parada mede o tamanho do estrago.
     */
    public static List<LinhaCorrecao> aCorrecao() {
        List<LinhaCorrecao> linhas = new ArrayList<>();

        for (var chegadas : Chegadas.todas(5_000, 200, SEMENTE).entrySet())
            for (var estrategia : Chegadas.asEstrategias().entrySet()) {
                Juiz.Veredito veredito = Juiz.julgar(aparelho(), estrategia.getValue(),
                        chegadas.getValue());

                linhas.add(new LinhaCorrecao(estrategia.getKey(), chegadas.getKey(),
                        veredito.itensEsperados(), veredito.itensGravados(),
                        veredito.itensPerdidos()));
            }

        return linhas;
    }

    // ───────────────────── 2. a carga ─────────────────────

    public record LinhaCarga(long intervalo, String estrategia, double gravacoesPorItem,
                             double itensPorGravacao, double ocupacao, double latenciaMedia) {
    }

    /**
     * GRAVACOES POR ITEM POR CARGA, com chegadas sem memoria.
     *
     * <p>O intervalo medio entre chegadas vai de 5.000 tiques, que e transito ralo, a 15,
     * que e mais do que o aparelho aguenta sem agrupar.
     *
     * <p>A coluna central e a razao de o agrupamento existir: 1,0 quer dizer uma gravacao
     * por item, e 0,01 quer dizer cem itens por gravacao.
     *
     * <p>A ADAPTATIVA FAZ ISSO SOZINHA. Com transito ralo ela grava um por um, porque nao ha
     * com quem agrupar, e a coluna dela fica em 1,0 igual a do juiz. Com transito pesado o
     * lote dela cresce exatamente na medida em que o aparelho nao da conta.
     */
    public static List<LinhaCarga> aCarga() {
        List<LinhaCarga> linhas = new ArrayList<>();

        for (long intervalo : new long[] {5_000, 1_000, 200, 50, 15}) {
            long[] chegadas = Chegadas.semMemoria(ITENS, intervalo, SEMENTE);

            for (var estrategia : Chegadas.asEstrategias().entrySet()) {
                if (estrategia.getKey().equals("contagem 32")) continue;

                Contador contador = new Contador();

                aparelho().rodar(chegadas, estrategia.getValue(), contador);

                linhas.add(new LinhaCarga(intervalo, estrategia.getKey(),
                        contador.gravacoesPorItem(), contador.itensPorGravacao(),
                        contador.ocupacao(),
                        contador.latenciaMedia() / CUSTO_FIXO));
            }
        }

        return linhas;
    }

    // ───────────────────── 3. a latencia ─────────────────────

    public record LinhaLatencia(long intervalo, String estrategia, double media,
                                double percentil99, double maior, double gravacoesPorItem) {
    }

    /**
     * A LATENCIA QUE O AGRUPAMENTO ACRESCENTA, em custos fixos.
     *
     * <p>1,0 na coluna quer dizer "esperou o tempo de uma gravacao", que e o minimo
     * possivel: ninguem fica duravel antes de a gravacao acabar.
     *
     * <p>A coluna do percentil 99 existe porque media nao e experiencia. Uma estrategia que
     * agrupa bem tem media baixa e um item esquecido esperando o lote encher.
     */
    public static List<LinhaLatencia> aLatencia() {
        List<LinhaLatencia> linhas = new ArrayList<>();

        for (long intervalo : new long[] {5_000, 200, 15}) {
            long[] chegadas = Chegadas.semMemoria(ITENS, intervalo, SEMENTE);

            for (var estrategia : Chegadas.asEstrategias().entrySet()) {
                if (estrategia.getKey().equals("contagem 32")) continue;

                Contador contador = new Contador();

                aparelho().rodar(chegadas, estrategia.getValue(), contador);

                linhas.add(new LinhaLatencia(intervalo, estrategia.getKey(),
                        contador.latenciaMedia() / CUSTO_FIXO,
                        contador.latenciaNoPercentil(99) / CUSTO_FIXO,
                        contador.maiorLatencia() / (double) CUSTO_FIXO,
                        contador.gravacoesPorItem()));
            }
        }

        return linhas;
    }

    // ───────────────────── 4. o parametro ─────────────────────

    public record LinhaParametro(long intervalo, String estrategia, int parametros,
                                 double gravacoesPorItem, double latenciaMedia,
                                 double pesandoALatencia, double pesandoAGravacao) {
    }

    /**
     * O PARAMETRO SO SERVE NA CARGA EM QUE FOI AJUSTADO, e as duas colunas da direita
     * DISCORDAM sobre isso.
     *
     * <p>Cada uma compara a estrategia com a adaptativa na mesma carga, somando as duas
     * contas que importam. A primeira conta uma gravacao por item como valendo o mesmo que
     * um custo fixo de latencia; a segunda conta a gravacao valendo DEZ vezes isso.
     *
     * <p>E elas dao vencedores diferentes. Com intervalo de 200, a adaptativa gasta 0,19
     * gravacoes por item e espera 1,59; a por tempo gasta 0,09 e espera 2,21. Quem ganha
     * depende inteiramente de quanto uma gravacao a mais incomoda em relacao a um tique de
     * espera, e essa e uma pergunta sobre o sistema, nao sobre o algoritmo.
     *
     * <p>Eu tinha escrito so a primeira coluna, e com ela a adaptativa ganhava em todas as
     * cargas. Com a segunda ela perde em tres das cinco. A conclusao honesta nao e qual
     * ganha: e que uma coluna so escondia a escolha que eu tinha feito sem dizer.
     *
     * <p>O que continua valendo sem depender de peso nenhum: a adaptativa tem ZERO
     * parametros e nao foi ajustada para carga nenhuma.
     */
    public static List<LinhaParametro> oParametro() {
        List<LinhaParametro> linhas = new ArrayList<>();

        for (long intervalo : new long[] {5_000, 1_000, 200, 50, 15}) {
            long[] chegadas = Chegadas.semMemoria(ITENS, intervalo, SEMENTE);

            Contador daAdaptativa = new Contador();

            aparelho().rodar(chegadas, new Estrategia.Adaptativa(), daAdaptativa);

            double pesoLatencia = daAdaptativa.gravacoesPorItem()
                    + daAdaptativa.latenciaMedia() / CUSTO_FIXO;

            double pesoGravacao = 10 * daAdaptativa.gravacoesPorItem()
                    + daAdaptativa.latenciaMedia() / CUSTO_FIXO;

            for (var estrategia : Chegadas.asEstrategias().entrySet()) {
                if (estrategia.getKey().equals("contagem 32")) continue;

                Contador contador = new Contador();

                aparelho().rodar(chegadas, estrategia.getValue(), contador);

                double comLatencia = contador.gravacoesPorItem()
                        + contador.latenciaMedia() / CUSTO_FIXO;

                double comGravacao = 10 * contador.gravacoesPorItem()
                        + contador.latenciaMedia() / CUSTO_FIXO;

                linhas.add(new LinhaParametro(intervalo, estrategia.getKey(),
                        estrategia.getValue().parametros(), contador.gravacoesPorItem(),
                        contador.latenciaMedia() / CUSTO_FIXO,
                        comLatencia / pesoLatencia, comGravacao / pesoGravacao));
            }
        }

        return linhas;
    }

    // ───────────────────── 5. a parada ─────────────────────

    public record LinhaParada(String estrategia, int itens, int gravados, int perdidos,
                              double fracaoPerdida) {
    }

    /**
     * O QUE ACONTECE QUANDO O TRANSITO PARA.
     *
     * <p>A historia acaba com o lote pela metade. A estrategia por contagem nunca grava
     * esses itens, porque a condicao dela e "juntar tantos" e nao vai juntar mais nenhum.
     *
     * <p>Isso nao e uma latencia alta que alguem poderia aceitar: e PERDA DE DADO, e o
     * tamanho dela nao depende do tempo de espera, depende de quantos itens calharam de
     * estar no lote quando tudo parou.
     *
     * <p>E o conserto e conhecido e custa um parametro a mais, que e exatamente o que a
     * tabela do parametro mede.
     */
    public static List<LinhaParada> aParada() {
        List<LinhaParada> linhas = new ArrayList<>();

        /* Um numero de itens que NAO e multiplo de 32, para o lote acabar pela metade. */
        int itens = 1_000;
        long[] chegadas = Chegadas.regulares(itens, 100);

        for (var estrategia : Chegadas.asEstrategias().entrySet()) {
            Contador contador = new Contador();

            Aparelho.Resultado resultado =
                    aparelho().rodar(chegadas, estrategia.getValue(), contador);

            linhas.add(new LinhaParada(estrategia.getKey(), itens, resultado.itensGravados(),
                    resultado.itensPerdidos(),
                    (double) resultado.itensPerdidos() / itens));
        }

        return linhas;
    }
}
