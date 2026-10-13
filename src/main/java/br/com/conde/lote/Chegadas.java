package br.com.conde.lote;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DE ONDE VEM O TRANSITO. Os instantes em que os itens chegam, em ordem crescente.
 *
 * <p>A forma da chegada decide qual estrategia ganha, e e por isso que ha mais de uma. Com
 * chegadas espacadas por igual, um lote de tamanho fixo acerta sempre; com rajadas, ele
 * fica metade do tempo esperando e a outra metade com fila.
 */
public final class Chegadas {

    private Chegadas() {
    }

    /**
     * O SORTEIO DA CASA: um gerador congruente linear escrito aqui, com constantes fixas.
     *
     * <p>Nao e {@link java.util.Random} de proposito. Uma medida que depende de uma
     * biblioteca nao e reproduzivel: a implementacao pode mudar entre versoes da maquina
     * virtual e as tabelas do README deixariam de bater sem que nada aqui tivesse mudado.
     */
    public static final class Sorteio {

        private long estado;

        public Sorteio(long semente) {
            this.estado = semente;
        }

        /**
         * O proximo inteiro.
         *
         * <p>O DESLOCAMENTO DE 32 CARREGA O METODO. Os bits BAIXOS de um gerador congruente
         * linear de modulo potencia de dois sao pessimos: o ultimo bit alterna zero e um
         * para sempre.
         *
         * <p>Num repositorio sobre agrupamento isso seria fatal e silencioso: um intervalo
         * entre chegadas com periodo curto faria o tamanho do lote cair num padrao, e as
         * estrategias por contagem pareceriam boas por terem calhado de acertar o periodo.
         * Ha um teste so para isto.
         */
        public int proximo() {
            estado = estado * 6364136223846793005L + 1442695040888963407L;
            return (int) (estado >>> 32);
        }

        /** Uma fracao em (0, 1), aberta: o logaritmo de zero nao existe. */
        public double fracao() {
            return ((proximo() & 0x7FFFFFFF) + 0.5) / 0x80000000L;
        }
    }

    /** Chegadas espacadas por igual. O melhor caso de qualquer estrategia. */
    public static long[] regulares(int quantos, long intervalo) {
        long[] chegadas = new long[quantos];

        for (int i = 0; i < quantos; i++) chegadas[i] = (i + 1) * intervalo;

        return chegadas;
    }

    /**
     * Chegadas sem memoria, com o intervalo medio pedido.
     *
     * <p>E a distribuicao padrao de transito que vem de muitas fontes independentes, e e a
     * unica em que "quanto falta para a proxima" nao depende de quanto tempo ja se esperou.
     */
    public static long[] semMemoria(int quantos, double intervaloMedio, long semente) {
        Sorteio sorteio = new Sorteio(semente);
        long[] chegadas = new long[quantos];
        long agora = 0;

        for (int i = 0; i < quantos; i++) {
            agora += Math.max(1, Math.round(-intervaloMedio * Math.log(sorteio.fracao())));
            chegadas[i] = agora;
        }

        return chegadas;
    }

    /**
     * Rajadas: pedacos de muitos itens juntos, separados por silencio.
     *
     * <p>E como transito de verdade costuma ser, e e o caso em que um lote de tamanho fixo
     * mais sofre: dentro da rajada ele enche na hora, e no silencio ele fica esperando
     * companhia que nao vem.
     */
    public static long[] emRajadas(int quantos, int porRajada, long dentroDaRajada,
                                   long entreRajadas) {

        long[] chegadas = new long[quantos];
        long agora = 0;

        for (int i = 0; i < quantos; i++) {
            if (i > 0 && i % porRajada == 0) agora += entreRajadas;
            else agora += dentroDaRajada;

            chegadas[i] = agora;
        }

        return chegadas;
    }

    public static Map<String, long[]> todas(int quantos, long intervalo, long semente) {
        Map<String, long[]> entradas = new LinkedHashMap<>();

        entradas.put("regulares", regulares(quantos, intervalo));
        entradas.put("sem memoria", semMemoria(quantos, intervalo, semente));
        entradas.put("em rajadas", emRajadas(quantos, 50, intervalo / 10,
                Math.max(1, intervalo * 50)));

        return entradas;
    }

    /** As estrategias que as tabelas percorrem, cada uma com o nome que a tabela imprime. */
    public static Map<String, Estrategia> asEstrategias() {
        Map<String, Estrategia> estrategias = new LinkedHashMap<>();

        estrategias.put("um por um", new Estrategia.UmPorUm());
        estrategias.put("contagem 32", new Estrategia.PorContagem(32));
        estrategias.put("tempo 2000", new Estrategia.PorTempo(2_000));
        estrategias.put("contagem 32 prazo 2000",
                new Estrategia.PorContagemComPrazo(32, 2_000));
        estrategias.put("adaptativa", new Estrategia.Adaptativa());

        return estrategias;
    }
}
