package br.com.conde.lote;

/**
 * O JUIZ: a estrategia que grava um por um.
 *
 * <p>Ela e o juiz certo e nao so o conveniente. Nao ha nela lote a juntar, prazo a vencer
 * nem decisao a tomar: chegou, grava. E e ela que da a menor latencia possivel e o maior
 * numero de gravacoes possivel, que sao os dois extremos contra os quais todo o resto e
 * lido.
 *
 * <h2>O que ele exige</h2>
 *
 * <p>Que TODO ITEM SEJA GRAVADO, exatamente uma vez, e que nenhum seja gravado antes de ter
 * chegado. Essas tres coisas sao a correcao do problema, e elas nao tem nada a ver com
 * quantas gravacoes foram gastas.
 *
 * <p>A estrategia por contagem FALHA nisso, e falha de um jeito que nenhuma medida de
 * desempenho mostraria: quando o transito para com o lote pela metade, os itens que ficaram
 * nunca sao gravados. Isso nao e lentidao, e perda de dado.
 */
public final class Juiz {

    private Juiz() {
    }

    public record Veredito(int itensEsperados, int itensGravados, int itensPerdidos,
                           String falha) {

        public boolean passou() {
            return falha == null;
        }
    }

    public static Veredito julgar(Aparelho aparelho, Estrategia estrategia, long[] chegadas) {
        Contador contador = new Contador();
        Aparelho.Resultado resultado = aparelho.rodar(chegadas, estrategia, contador);

        String falha = null;

        if (resultado.itensPerdidos() > 0)
            falha = resultado.itensPerdidos() + " itens nunca foram gravados";

        else if (resultado.itensGravados() != chegadas.length)
            falha = "gravou " + resultado.itensGravados() + " de " + chegadas.length;

        else if (contador.itensGravados() != chegadas.length)
            falha = "o contador viu " + contador.itensGravados() + " itens";

        /*
         * NENHUMA LATENCIA PODE SER NEGATIVA, e essa conferencia nao e obvia: ela pega um
         * item gravado ANTES de chegar, que e o erro que uma simulacao com o relogio
         * desalinhado produz e que nenhuma outra conta denuncia.
         */
        else if (contador.maiorLatencia() < 0) falha = "latencia negativa";

        else if (contador.latenciaMedia() < aparelho.custoFixo())
            falha = "latencia media de " + contador.latenciaMedia()
                    + " abaixo do custo fixo de " + aparelho.custoFixo();

        return new Veredito(chegadas.length, resultado.itensGravados(),
                resultado.itensPerdidos(), falha);
    }

    /**
     * Confere que a estrategia nunca grava menos itens por gravacao do que o aparelho
     * aguentaria, dado o que estava esperando.
     *
     * <p>E uma conferencia de desperdicio e nao de correcao, e ela existe para distinguir
     * "agrupou pouco porque nao havia com quem" de "agrupou pouco porque decidiu mal".
     */
    public static double gravacoesMinimasPossiveis(Aparelho aparelho, long[] chegadas) {
        if (chegadas.length == 0) return 0;

        long periodo = chegadas[chegadas.length - 1] - chegadas[0];

        if (periodo <= 0) return 1.0 / chegadas.length;

        /*
         * O MINIMO TEORICO: se todas as gravacoes fossem do maior lote que o tempo permite,
         * quantas seriam. Ele e um limite inferior frouxo e serve para o que precisa: dizer
         * que nenhuma estrategia pode fazer melhor do que isso.
         */
        double maiorLotePossivel = chegadas.length;
        double duracao = aparelho.duracaoDe((int) maiorLotePossivel);

        return duracao > periodo ? 1.0 / chegadas.length : 1.0 / maiorLotePossivel;
    }
}
