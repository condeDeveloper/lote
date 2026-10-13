package br.com.conde.lote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoteTest {

    private static final long CUSTO_FIXO = 1_000;
    private static final long CUSTO_POR_ITEM = 10;

    private static Aparelho aparelho() {
        return new Aparelho(CUSTO_FIXO, CUSTO_POR_ITEM);
    }

    // ───────────────────── a correcao ─────────────────────

    /**
     * TODO ITEM GRAVADO UMA VEZ SO, e uma das estrategias falha nisso.
     *
     * <p>A falha dela nao e lentidao: e perda de dado, e nenhuma medida de desempenho
     * mostraria.
     */
    @Test
    @DisplayName("quatro das cinco estrategias gravam todo item, e a por contagem perde")
    void aPorContagemPerdeItem() {
        List<String> comPerda = new ArrayList<>();

        for (var chegadas : Chegadas.todas(1_000, 200, 7).entrySet())
            for (var estrategia : Chegadas.asEstrategias().entrySet()) {
                Juiz.Veredito veredito = Juiz.julgar(aparelho(), estrategia.getValue(),
                        chegadas.getValue());

                if (veredito.passou()) continue;

                comPerda.add(estrategia.getKey());

                assertThat(veredito.falha())
                        .as(estrategia.getKey() + " em " + chegadas.getKey())
                        .contains("nunca foram gravados");
            }

        assertThat(comPerda).containsOnly("contagem 32");
        assertThat(comPerda).isNotEmpty();
    }

    @Test
    @DisplayName("as outras quatro gravam todo item em toda forma de chegada")
    void asOutrasGravamTudo() {
        for (int quantos : new int[] {1, 2, 33, 500, 5_000})
            for (var chegadas : Chegadas.todas(quantos, 300, 11).entrySet())
                for (var estrategia : Chegadas.asEstrategias().entrySet()) {
                    if (estrategia.getValue() instanceof Estrategia.PorContagem) continue;

                    Juiz.Veredito veredito = Juiz.julgar(aparelho(), estrategia.getValue(),
                            chegadas.getValue());

                    assertThat(veredito.passou())
                            .as(estrategia.getKey() + " em " + chegadas.getKey() + " com "
                                    + quantos + " itens: " + veredito.falha())
                            .isTrue();
                }
    }

    /**
     * NENHUM ITEM FICA DURAVEL ANTES DE A GRAVACAO ACABAR, e isso e o piso de tudo.
     *
     * <p>A menor latencia possivel e um custo fixo, porque e quanto uma gravacao com um
     * item so demora. Uma latencia abaixo disso seria um item declarado duravel antes de o
     * aparelho confirmar.
     */
    @Test
    @DisplayName("a menor latencia possivel e o custo de uma gravacao, e ninguem fica abaixo")
    void ninguemFicaDuravelAntesDaHora() {
        for (var estrategia : Chegadas.asEstrategias().entrySet()) {
            Contador contador = new Contador();

            aparelho().rodar(Chegadas.regulares(2_000, 5_000), estrategia.getValue(), contador);

            if (contador.itensGravados() == 0) continue;

            assertThat(contador.latenciaMedia())
                    .as(estrategia.getKey())
                    .isGreaterThanOrEqualTo(CUSTO_FIXO + CUSTO_POR_ITEM);
        }
    }

    @Test
    @DisplayName("sem itens nenhum, nada e gravado e nada e perdido")
    void semItens() {
        for (var estrategia : Chegadas.asEstrategias().entrySet()) {
            Contador contador = new Contador();

            Aparelho.Resultado resultado =
                    aparelho().rodar(new long[0], estrategia.getValue(), contador);

            assertThat(resultado.itensGravados()).as(estrategia.getKey()).isZero();
            assertThat(resultado.itensPerdidos()).as(estrategia.getKey()).isZero();
            assertThat(contador.gravacoes()).as(estrategia.getKey()).isZero();
        }
    }

    @Test
    @DisplayName("custo e parametro invalidos sao recusados")
    void parametrosInvalidos() {
        assertThatThrownBy(() -> new Aparelho(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Estrategia.PorContagem(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Estrategia.PorTempo(-1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Estrategia.PorContagemComPrazo(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────── o que cada estrategia promete sobre si ─────────────────────

    @Test
    @DisplayName("a um por um gasta exatamente uma gravacao por item, sempre")
    void aUmPorUmNuncaAgrupa() {
        for (var chegadas : Chegadas.todas(2_000, 100, 3).entrySet()) {
            Contador contador = new Contador();

            aparelho().rodar(chegadas.getValue(), new Estrategia.UmPorUm(), contador);

            assertThat(contador.gravacoesPorItem()).as(chegadas.getKey()).isEqualTo(1.0);
            assertThat(contador.maiorLote()).as(chegadas.getKey()).isEqualTo(1);
        }
    }

    /**
     * A ADAPTATIVA NAO ESPERA DE PROPOSITO, e com transito ralo ela nao agrupa nada.
     *
     * <p>Isso e a propriedade dela: o agrupamento acontece so quando ele e inevitavel,
     * porque o aparelho esta ocupado.
     */
    @Test
    @DisplayName("com transito ralo a adaptativa grava um por um, igual ao juiz")
    void aAdaptativaNaoAgrupaSemNecessidade() {
        long[] espacadas = Chegadas.regulares(1_000, 10 * CUSTO_FIXO);

        Contador daAdaptativa = new Contador();
        Contador doJuiz = new Contador();

        aparelho().rodar(espacadas, new Estrategia.Adaptativa(), daAdaptativa);
        aparelho().rodar(espacadas, new Estrategia.UmPorUm(), doJuiz);

        assertThat(daAdaptativa.gravacoesPorItem()).isEqualTo(1.0);
        assertThat(daAdaptativa.latenciaMedia()).isEqualTo(doJuiz.latenciaMedia());
        assertThat(daAdaptativa.maiorLote()).isEqualTo(1);
    }

    @Test
    @DisplayName("com transito pesado a adaptativa agrupa sozinha, sem parametro nenhum")
    void aAdaptativaAgrupaSozinha() {
        long[] apertadas = Chegadas.regulares(20_000, 15);

        Contador contador = new Contador();

        aparelho().rodar(apertadas, new Estrategia.Adaptativa(), contador);

        assertThat(contador.itensPorGravacao())
                .as("o lote cresce ate o aparelho dar conta")
                .isGreaterThan(50.0);

        assertThat(contador.ocupacao())
                .as("e o aparelho fica ocupado o tempo todo, que e o limite")
                .isCloseTo(1.0, within(0.01));
    }

    /**
     * A POR TEMPO COBRA O PRAZO INTEIRO ATE COM O APARELHO LIVRE.
     *
     * <p>Esse e o preco do parametro dela: ela nao sabe que nao ha com quem agrupar, entao
     * espera do mesmo jeito.
     */
    @Test
    @DisplayName("a por tempo faz o item esperar o prazo mesmo sem ninguem para agrupar")
    void aPorTempoEsperaAToa() {
        long[] espacadas = Chegadas.regulares(500, 10 * CUSTO_FIXO);

        Contador daPorTempo = new Contador();
        Contador daAdaptativa = new Contador();

        aparelho().rodar(espacadas, new Estrategia.PorTempo(3_000), daPorTempo);
        aparelho().rodar(espacadas, new Estrategia.Adaptativa(), daAdaptativa);

        assertThat(daPorTempo.gravacoesPorItem())
                .as("ela agrupa o mesmo que a adaptativa aqui, que e nada")
                .isEqualTo(daAdaptativa.gravacoesPorItem());

        assertThat(daPorTempo.latenciaMedia())
                .as("e cobra tres mil tiques a mais por item, sem economizar nada")
                .isCloseTo(daAdaptativa.latenciaMedia() + 3_000, within(1.0));
    }

    /**
     * O PRAZO SO VENCE SE ALGUEM OLHAR, e esse teste existe por causa de um bug.
     *
     * <p>A primeira versao da simulacao so reperguntava a estrategia quando alguem chegava.
     * Com um item esperando e ninguem mais vindo, o prazo vencia e ninguem olhava: o item
     * ficava ate a proxima chegada.
     */
    @Test
    @DisplayName("um item sozinho e gravado quando o prazo vence, sem esperar outra chegada")
    void oPrazoVenceSozinho() {
        long[] umSoEDepoisMuitoDepois = {100, 1_000_000};

        Contador contador = new Contador();

        aparelho().rodar(umSoEDepoisMuitoDepois, new Estrategia.PorTempo(500), contador);

        assertThat(contador.gravacoes()).isEqualTo(2);

        assertThat(contador.maiorLatencia())
                .as("o primeiro item espera o prazo mais a gravacao, e nao ate 1.000.000")
                .isEqualTo(500 + CUSTO_FIXO + CUSTO_POR_ITEM);
    }

    @Test
    @DisplayName("a por contagem com prazo nao perde item, que e o conserto dela")
    void oConsertoDaPorContagem() {
        long[] chegadas = Chegadas.regulares(100, 50);

        Contador semPrazo = new Contador();
        Contador comPrazo = new Contador();

        Aparelho.Resultado sem = aparelho().rodar(chegadas,
                new Estrategia.PorContagem(32), semPrazo);

        Aparelho.Resultado com = aparelho().rodar(chegadas,
                new Estrategia.PorContagemComPrazo(32, 2_000), comPrazo);

        assertThat(sem.itensPerdidos()).isGreaterThan(0);
        assertThat(com.itensPerdidos()).isZero();
        assertThat(com.itensGravados()).isEqualTo(100);
    }

    // ───────────────────── o aparelho ─────────────────────

    @Test
    @DisplayName("o custo fixo e o que faz agrupar valer a pena")
    void oCustoFixoEARazaoDeTudo() {
        Aparelho aparelho = aparelho();

        assertThat(aparelho.duracaoDe(1)).isEqualTo(1_010);
        assertThat(aparelho.duracaoDe(100)).isEqualTo(2_000);

        assertThat(aparelho.duracaoDe(100))
                .as("cem itens juntos custam menos que o dobro de um sozinho")
                .isLessThan(2 * aparelho.duracaoDe(1));

        assertThat(100 * aparelho.duracaoDe(1) / (double) aparelho.duracaoDe(100))
                .as("e cinquenta vezes menos que cem gravacoes separadas")
                .isCloseTo(50.5, within(0.1));

        assertThat(aparelho.vazaoComLote(100)).isGreaterThan(aparelho.vazaoComLote(1) * 40);
    }

    // ───────────────────── as chegadas ─────────────────────

    @Test
    @DisplayName("as chegadas saem em ordem crescente em todos os geradores")
    void asChegadasSaoCrescentes() {
        for (var entrada : Chegadas.todas(5_000, 100, 5).entrySet()) {
            long[] chegadas = entrada.getValue();

            for (int i = 1; i < chegadas.length; i++)
                assertThat(chegadas[i]).as(entrada.getKey() + " na posicao " + i)
                        .isGreaterThanOrEqualTo(chegadas[i - 1]);

            assertThat(chegadas[0]).as(entrada.getKey()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("as rajadas sao rajadas mesmo: juntas por dentro e separadas por fora")
    void asRajadasSaoRajadas() {
        long[] rajadas = Chegadas.emRajadas(200, 50, 10, 5_000);

        assertThat(rajadas[1] - rajadas[0]).isEqualTo(10);
        assertThat(rajadas[49] - rajadas[48]).isEqualTo(10);

        assertThat(rajadas[50] - rajadas[49])
                .as("entre uma rajada e a proxima, o silencio")
                .isEqualTo(5_000);
    }

    // ───────────────────── o sorteio da casa ─────────────────────

    /**
     * O TESTE DO DESLOCAMENTO DE 32.
     *
     * <p>Aqui ele seria fatal e silencioso: um intervalo entre chegadas com periodo curto
     * faria o tamanho do lote cair num padrao, e as estrategias por contagem pareceriam
     * boas por terem calhado de acertar o periodo.
     */
    @Test
    @DisplayName("o deslocamento de 32 e o que faz os intervalos nao terem periodo")
    void oDeslocamentoDeTrintaEDois() {
        Chegadas.Sorteio sorteio = new Chegadas.Sorteio(1);
        int[] quantos = new int[10];

        for (int i = 0; i < 100_000; i++) quantos[(int) (sorteio.fracao() * 10)]++;

        for (int faixa = 0; faixa < 10; faixa++)
            assertThat(quantos[faixa]).as("faixa " + faixa).isBetween(9_600, 10_400);

        long estado = 1;
        int alternancias = 0;
        int anterior = -1;

        for (int i = 0; i < 1_000; i++) {
            estado = estado * 6364136223846793005L + 1442695040888963407L;

            int baixo = (int) (estado & 1);

            if (anterior >= 0 && baixo != anterior) alternancias++;

            anterior = baixo;
        }

        assertThat(alternancias)
                .as("o bit baixo do estado cru alterna em TODAS, que e um periodo de dois")
                .isEqualTo(999);
    }

    @Test
    @DisplayName("o percentil ordena de verdade e o 100 e o maximo")
    void oPercentil() {
        Contador contador = new Contador();

        for (long latencia : new long[] {50, 10, 30, 20, 40}) contador.durou(latencia);

        assertThat(contador.latenciaNoPercentil(0)).isEqualTo(10.0);
        assertThat(contador.latenciaNoPercentil(50)).isEqualTo(30.0);
        assertThat(contador.latenciaNoPercentil(100)).isEqualTo(50.0);
        assertThat(contador.maiorLatencia()).isEqualTo(50);
        assertThat(contador.latenciaMedia()).isEqualTo(30.0);
    }
}
