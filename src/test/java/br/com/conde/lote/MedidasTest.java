package br.com.conde.lote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os numeros do README, afirmados.
 *
 * <p>Um README e texto: ele nao quebra quando deixa de ser verdade. Cada afirmacao das
 * tabelas esta repetida aqui para quebrar.
 */
class MedidasTest {

    /* As tabelas sao calculadas uma vez so: cada uma simula centenas de milhares de itens. */
    private static final List<Medidas.LinhaCarga> CARGA = Medidas.aCarga();
    private static final List<Medidas.LinhaLatencia> LATENCIA = Medidas.aLatencia();
    private static final List<Medidas.LinhaParametro> PARAMETRO = Medidas.oParametro();
    private static final List<Medidas.LinhaParada> PARADA = Medidas.aParada();

    // ───────────────────── 1. a correcao ─────────────────────

    @Test
    @DisplayName("so a estrategia por contagem perde item, e as outras quatro nao")
    void soAPorContagemPerde() {
        List<Medidas.LinhaCorrecao> linhas = Medidas.aCorrecao();

        assertThat(linhas).hasSize(15);

        assertThat(linhas).filteredOn(l -> l.perdidos() > 0)
                .isNotEmpty()
                .allSatisfy(l -> assertThat(l.estrategia()).isEqualTo("contagem 32"));

        assertThat(linhas).filteredOn(l -> !l.estrategia().equals("contagem 32"))
                .allSatisfy(l -> {
                    assertThat(l.perdidos()).as(l.estrategia()).isZero();
                    assertThat(l.gravados()).as(l.estrategia()).isEqualTo(l.esperados());
                });
    }

    // ───────────────────── 2. a carga ─────────────────────

    /**
     * A ADAPTATIVA AGRUPA SOZINHA, e a prova esta nos dois extremos.
     *
     * <p>Com transito ralo ela grava um por um, porque nao ha com quem agrupar. Com
     * transito pesado o lote dela chega a 192 itens.
     */
    @Test
    @DisplayName("a adaptativa vai de um item por gravacao a 192, sem parametro nenhum")
    void aAdaptativaAgrupaSozinha() {
        assertThat(carga(5_000, "adaptativa").itensPorGravacao())
                .as("com transito ralo ela quase nao agrupa")
                .isCloseTo(1.02, within(0.01));

        assertThat(carga(15, "adaptativa").itensPorGravacao())
                .as("e com transito pesado o lote cresce ate 192")
                .isCloseTo(192.68, within(0.01));

        assertThat(carga(15, "adaptativa").gravacoesPorItem()).isCloseTo(0.0052, within(0.0001));

        assertThat(carga(15, "adaptativa").ocupacao())
                .as("ali o aparelho fica ocupado o tempo todo, que e o limite")
                .isCloseTo(1.0, within(0.001));
    }

    /**
     * O JUIZ COLAPSA QUANDO A CARGA PASSA DO QUE ELE AGUENTA.
     *
     * <p>Gravar um por um custa 1.010 tiques por item. Com chegadas a cada 200, a ocupacao
     * vai a 1,0 e a fila nunca mais encolhe.
     */
    @Test
    @DisplayName("a um por um colapsa em carga alta: latencia de 40 mil custos fixos")
    void aUmPorUmColapsa() {
        assertThat(carga(5_000, "um por um").latenciaMedia()).isCloseTo(1.13, within(0.01));
        assertThat(carga(200, "um por um").latenciaMedia()).isCloseTo(40384.08, within(0.01));

        /*
         * 0,9999978 e nao 1,0 exato: o aparelho fica parado nos primeiros instantes, antes
         * de o primeiro item chegar, e esse pedacinho nunca sai da media.
         */
        assertThat(carga(200, "um por um").ocupacao()).isCloseTo(1.0, within(0.00001));

        for (long intervalo : new long[] {5_000, 1_000, 200, 50, 15})
            assertThat(carga(intervalo, "um por um").gravacoesPorItem())
                    .as("e ela nunca agrupa, em carga nenhuma")
                    .isEqualTo(1.0);
    }

    private static Medidas.LinhaCarga carga(long intervalo, String estrategia) {
        return CARGA.stream()
                .filter(l -> l.intervalo() == intervalo && l.estrategia().equals(estrategia))
                .findFirst()
                .orElseThrow();
    }

    // ───────────────────── 3. a latencia ─────────────────────

    @Test
    @DisplayName("com transito ralo, a por tempo cobra o prazo e a adaptativa nao")
    void aPorTempoCobraOPrazo() {
        assertThat(latencia(5_000, "adaptativa").media()).isCloseTo(1.11, within(0.01));
        assertThat(latencia(5_000, "tempo 2000").media()).isCloseTo(2.73, within(0.01));

        assertThat(latencia(5_000, "tempo 2000").media())
                .as("ela faz o item esperar sem ter com quem agrupar")
                .isGreaterThan(latencia(5_000, "adaptativa").media() * 2);

        assertThat(latencia(5_000, "adaptativa").media())
                .as("e a adaptativa fica praticamente no minimo, que e um custo fixo")
                .isLessThan(1.2);
    }

    @Test
    @DisplayName("o percentil 99 e sempre maior que a media, e e nele que a cauda aparece")
    void aCaudaAparece() {
        assertThat(LATENCIA).allSatisfy(l -> {
            assertThat(l.percentil99())
                    .as(l.estrategia() + " com intervalo " + l.intervalo())
                    .isGreaterThanOrEqualTo(l.media());

            assertThat(l.maior()).isGreaterThanOrEqualTo(l.percentil99());
        });

        assertThat(latencia(200, "um por um").maior()).isCloseTo(80826.53, within(0.01));
    }

    private static Medidas.LinhaLatencia latencia(long intervalo, String estrategia) {
        return LATENCIA.stream()
                .filter(l -> l.intervalo() == intervalo && l.estrategia().equals(estrategia))
                .findFirst()
                .orElseThrow();
    }

    // ───────────────────── 4. o parametro ─────────────────────

    /**
     * A CORRECAO: as duas colunas discordam sobre quem ganha.
     *
     * <p>Eu tinha escrito so a primeira, e com ela a adaptativa ganhava em todas as cargas.
     * Com a segunda ela perde em tres das cinco.
     */
    @Test
    @DisplayName("pesando a latencia a adaptativa ganha sempre, e pesando a gravacao nao")
    void asDuasColunasDiscordam() {
        for (long intervalo : new long[] {5_000, 1_000, 200, 50, 15})
            assertThat(parametro(intervalo, "tempo 2000").pesandoALatencia())
                    .as("intervalo " + intervalo + ": pesando a latencia, a adaptativa ganha")
                    .isGreaterThanOrEqualTo(1.0);

        assertThat(parametro(5_000, "tempo 2000").pesandoAGravacao())
                .as("e pesando a gravacao ela perde com transito ralo")
                .isLessThan(1.0);

        assertThat(parametro(1_000, "tempo 2000").pesandoAGravacao()).isCloseTo(0.66, within(0.01));
        assertThat(parametro(200, "tempo 2000").pesandoAGravacao()).isCloseTo(0.89, within(0.01));

        long perdasDaAdaptativa = List.of(5_000L, 1_000L, 200L, 50L, 15L).stream()
                .filter(intervalo -> parametro(intervalo, "tempo 2000").pesandoAGravacao() < 1.0)
                .count();

        assertThat(perdasDaAdaptativa)
                .as("a adaptativa perde em tres das cinco cargas com o outro peso")
                .isEqualTo(3);
    }

    /**
     * E O QUE NAO DEPENDE DE PESO NENHUM: ela tem zero parametros.
     */
    @Test
    @DisplayName("a adaptativa tem zero parametros e nunca e a pior, com qualquer peso")
    void aAdaptativaNaoTemParametro() {
        assertThat(parametro(200, "adaptativa").parametros()).isZero();
        assertThat(parametro(200, "tempo 2000").parametros()).isEqualTo(1);
        assertThat(parametro(200, "contagem 32 prazo 2000").parametros()).isEqualTo(2);

        for (long intervalo : new long[] {5_000, 1_000, 200, 50, 15}) {
            assertThat(parametro(intervalo, "adaptativa").pesandoALatencia()).isEqualTo(1.0);
            assertThat(parametro(intervalo, "adaptativa").pesandoAGravacao()).isEqualTo(1.0);

            assertThat(parametro(intervalo, "um por um").pesandoAGravacao())
                    .as("intervalo " + intervalo + ": a adaptativa sempre ganha do juiz")
                    .isGreaterThan(1.0);
        }
    }

    @Test
    @DisplayName("com carga muito alta todas as que agrupam convergem para a mesma coisa")
    void comCargaAltaTodasConvergem() {
        for (String estrategia : List.of("tempo 2000", "contagem 32 prazo 2000", "adaptativa")) {
            assertThat(parametro(15, estrategia).gravacoesPorItem())
                    .as(estrategia)
                    .isCloseTo(0.0052, within(0.0001));

            assertThat(parametro(15, estrategia).pesandoALatencia())
                    .as(estrategia + ": o aparelho e o limite, e nao a estrategia")
                    .isCloseTo(1.0, within(0.01));
        }
    }

    private static Medidas.LinhaParametro parametro(long intervalo, String estrategia) {
        return PARAMETRO.stream()
                .filter(l -> l.intervalo() == intervalo && l.estrategia().equals(estrategia))
                .findFirst()
                .orElseThrow();
    }

    // ───────────────────── 5. a parada ─────────────────────

    /**
     * A PERDA NAO E LENTIDAO: e dado que nunca foi gravado.
     *
     * <p>Mil itens com lotes de 32 deixam oito presos no ultimo lote, para sempre.
     */
    @Test
    @DisplayName("a por contagem perde os oito itens do ultimo lote, e nunca os grava")
    void aParadaPerdeOUltimoLote() {
        assertThat(parada("contagem 32").perdidos()).isEqualTo(8);
        assertThat(parada("contagem 32").gravados()).isEqualTo(992);

        assertThat(parada("contagem 32").fracaoPerdida()).isCloseTo(0.008, within(0.0001));

        assertThat(1_000 % 32)
                .as("e oito e exatamente o resto de mil por trinta e dois")
                .isEqualTo(8);
    }

    @Test
    @DisplayName("as outras quatro gravam os mil, inclusive a por contagem com prazo")
    void asOutrasNaoPerdem() {
        for (String estrategia : List.of("um por um", "tempo 2000", "contagem 32 prazo 2000",
                "adaptativa")) {
            assertThat(parada(estrategia).perdidos()).as(estrategia).isZero();
            assertThat(parada(estrategia).gravados()).as(estrategia).isEqualTo(1_000);
        }
    }

    private static Medidas.LinhaParada parada(String estrategia) {
        return PARADA.stream()
                .filter(l -> l.estrategia().equals(estrategia))
                .findFirst()
                .orElseThrow();
    }
}
