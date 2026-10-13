package br.com.conde.lote;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * O APARELHO QUE GRAVA: um de cada vez, com custo fixo mais custo por item.
 *
 * <p>O custo FIXO e a razao de este repositorio existir. Uma gravacao duravel precisa
 * esperar o aparelho confirmar que o dado saiu, e essa espera e quase a mesma com um item
 * ou com mil. O custo por item e o que de fato depende do tamanho.
 *
 * <p>Com custo fixo de 1.000 e custo por item de 10, um lote de cem itens custa 2.000 em
 * vez dos 100.000 que custariam cem gravacoes separadas.
 *
 * <h2>A simulacao e dirigida a eventos</h2>
 *
 * <p>O relogio pula direto para o proximo acontecimento, que e ou uma chegada ou o fim de
 * uma gravacao, e entre dois acontecimentos nada muda. Um laco de tique em tique daria
 * exatamente o mesmo resultado e gastaria mil vezes mais.
 *
 * <p>O tempo e simulado e inteiro, e nunca o relogio da maquina: a mesma semente da a mesma
 * historia em qualquer lugar.
 */
public final class Aparelho {

    private final long custoFixo;
    private final long custoPorItem;

    public Aparelho(long custoFixo, long custoPorItem) {
        if (custoFixo < 0 || custoPorItem < 0)
            throw new IllegalArgumentException("custos nao negativos");

        this.custoFixo = custoFixo;
        this.custoPorItem = custoPorItem;
    }

    public long custoFixo() {
        return custoFixo;
    }

    public long custoPorItem() {
        return custoPorItem;
    }

    public long duracaoDe(int itens) {
        return custoFixo + custoPorItem * itens;
    }

    /** Quantos itens por unidade de tempo o aparelho aguenta com lotes deste tamanho. */
    public double vazaoComLote(int itens) {
        return itens / (double) duracaoDe(itens);
    }

    public record Resultado(int itensGravados, int itensPerdidos, long tempoTotal) {
    }

    /**
     * Roda a historia inteira: os itens chegam nos instantes dados e a estrategia decide.
     *
     * <p>Os itens que ainda estiverem esperando quando a historia acaba sao PERDIDOS, e
     * isso e deliberado. Uma estrategia que depende de mais transito para gravar o que ja
     * tem nao esta lenta: ela esta errada, e e assim que a tabela da parada mostra.
     */
    public Resultado rodar(long[] chegadas, Estrategia estrategia, Contador contador) {
        Deque<Long> esperando = new ArrayDeque<>();

        long agora = 0;
        long livreEm = 0;
        int proximaChegada = 0;
        int gravados = 0;

        while (proximaChegada < chegadas.length || !esperando.isEmpty()) {
            boolean aparelhoLivre = agora >= livreEm;

            /*
             * SO DA PARA DECIDIR COM O APARELHO LIVRE. Com ele ocupado, a unica coisa que
             * pode acontecer e chegar mais gente, e e por isso que o agrupamento da
             * estrategia adaptativa acontece sozinho: ela nao espera de proposito, ela so
             * nao pode gravar enquanto a gravacao anterior nao acaba.
             */
            if (aparelhoLivre && !esperando.isEmpty()
                    && estrategia.gravarAgora(esperando.size(), agora - esperando.peekFirst())) {

                int levar = Math.min(esperando.size(),
                        Math.max(1, estrategia.quantosLevar(esperando.size())));

                long duracao = duracaoDe(levar);
                long fim = agora + duracao;

                for (int i = 0; i < levar; i++) contador.durou(fim - esperando.pollFirst());

                contador.gravou(duracao, levar, levar);

                gravados += levar;
                livreEm = fim;
                agora = fim;

                continue;
            }

            /*
             * PARA ONDE O RELOGIO PULA. Tres coisas podem mudar a decisao: alguem chegar, a
             * gravacao em curso acabar, ou o prazo da estrategia vencer. A terceira e a que
             * faltava: sem ela, uma estrategia por tempo com o aparelho livre ficava parada
             * ate a proxima chegada, com o prazo ja vencido.
             */
            long proximo = Long.MAX_VALUE;

            if (proximaChegada < chegadas.length)
                proximo = Math.min(proximo, chegadas[proximaChegada]);

            if (!aparelhoLivre) proximo = Math.min(proximo, livreEm);

            if (aparelhoLivre && !esperando.isEmpty()) {
                long falta = estrategia.esperaAteDecidir(esperando.size(),
                        agora - esperando.peekFirst());

                if (falta != Long.MAX_VALUE) proximo = Math.min(proximo, agora + falta);
            }

            if (proximo == Long.MAX_VALUE) {
                /*
                 * NADA MAIS PODE ACONTECER e a estrategia nao quis gravar. Os pendentes
                 * estao PERDIDOS, e isso e deliberado: uma estrategia que depende de mais
                 * transito para gravar o que ja tem nao esta lenta, esta errada.
                 */
                contador.tempoTotal(agora);

                return new Resultado(gravados, esperando.size(), agora);
            }

            agora = Math.max(agora, proximo);

            while (proximaChegada < chegadas.length && chegadas[proximaChegada] <= agora)
                esperando.addLast(chegadas[proximaChegada++]);
        }

        contador.tempoTotal(agora);

        return new Resultado(gravados, 0, agora);
    }
}
