package br.com.conde.lote;

/**
 * O CONTADOR: gravacoes por item e latencia em tempo SIMULADO. Nunca segundos de relogio.
 *
 * <p>A GRAVACAO POR ITEM e a medida certa e nao so a conveniente. Agrupar existe por um
 * motivo so: uma gravacao duravel tem um custo FIXO que nao depende de quantos itens vao
 * nela. Juntar dez itens numa gravacao paga esse custo uma vez em vez de dez.
 *
 * <p>Entao 1,0 na coluna quer dizer "nao agrupou nada" e 0,1 quer dizer "dez itens por
 * gravacao". A coluna ja vem como fracao do que ela economiza.
 *
 * <h2>E a segunda medida anda ao contrario</h2>
 *
 * <p>Agrupar mais custa esperar mais: um item que chega sozinho e espera por companhia fica
 * parado sem precisar. As duas colunas sao a tensao inteira do problema, e uma estrategia
 * que so olhasse uma delas seria trivial de escrever e inutil.
 *
 * <p>A LATENCIA E MEDIDA NA CAUDA e nao so na media, porque e na cauda que ela aparece. Uma
 * estrategia que agrupa bem tem media baixa e um item perdido esperando o lote encher, e so
 * o maximo denuncia isso.
 */
public final class Contador {

    private long gravacoes;
    private long itensGravados;
    private long somaDeLatencia;
    private long maiorLatencia;
    private long tempoGravando;
    private long tempoTotal;
    private long maiorLote;

    private long[] latencias = new long[1024];
    private int quantasLatencias;

    /** Uma gravacao durou tanto e levou tantos itens. */
    public void gravou(long duracao, int itens, long maiorLoteAgora) {
        gravacoes++;
        itensGravados += itens;
        tempoGravando += duracao;
        maiorLote = Math.max(maiorLote, maiorLoteAgora);
    }

    /** Um item ficou durável, tendo esperado tanto desde que chegou. */
    public void durou(long latencia) {
        somaDeLatencia += latencia;
        maiorLatencia = Math.max(maiorLatencia, latencia);

        if (quantasLatencias == latencias.length)
            latencias = java.util.Arrays.copyOf(latencias, latencias.length * 2);

        latencias[quantasLatencias++] = latencia;
    }

    public void tempoTotal(long tempo) {
        tempoTotal = tempo;
    }

    public long gravacoes() {
        return gravacoes;
    }

    public long itensGravados() {
        return itensGravados;
    }

    public long maiorLote() {
        return maiorLote;
    }

    public long maiorLatencia() {
        return maiorLatencia;
    }

    public long tempoTotal() {
        return tempoTotal;
    }

    /** A coluna central: quantas gravacoes cada item custou. */
    public double gravacoesPorItem() {
        return itensGravados == 0 ? 0 : (double) gravacoes / itensGravados;
    }

    public double itensPorGravacao() {
        return gravacoes == 0 ? 0 : (double) itensGravados / gravacoes;
    }

    public double latenciaMedia() {
        return quantasLatencias == 0 ? 0 : (double) somaDeLatencia / quantasLatencias;
    }

    /**
     * A latencia no percentil pedido.
     *
     * <p>Ela existe porque media nao e experiencia. Uma estrategia que agrupa bem tem media
     * baixa e um item esquecido esperando o lote encher, e so o percentil alto ve isso.
     */
    public double latenciaNoPercentil(double percentil) {
        if (quantasLatencias == 0) return 0;

        long[] ordenadas = java.util.Arrays.copyOf(latencias, quantasLatencias);

        java.util.Arrays.sort(ordenadas);

        int posicao = (int) Math.min(quantasLatencias - 1L,
                Math.round(percentil / 100.0 * (quantasLatencias - 1)));

        return ordenadas[posicao];
    }

    /** A fracao do tempo em que o aparelho esteve gravando. Acima de 1 nao existe. */
    public double ocupacao() {
        return tempoTotal == 0 ? 0 : (double) tempoGravando / tempoTotal;
    }

    public void zerar() {
        gravacoes = 0;
        itensGravados = 0;
        somaDeLatencia = 0;
        maiorLatencia = 0;
        tempoGravando = 0;
        tempoTotal = 0;
        maiorLote = 0;
        quantasLatencias = 0;
    }
}
