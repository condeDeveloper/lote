package br.com.conde.lote;

/**
 * QUANDO COMECAR A PROXIMA GRAVACAO.
 *
 * <p>Esta e a unica decisao do problema. O aparelho grava um lote de cada vez e tem um
 * custo fixo por gravacao; os itens chegam quando chegam. A estrategia e chamada toda vez
 * que o aparelho fica livre e toda vez que um item chega, e responde uma coisa: grava
 * agora, ou nao.
 *
 * <p>E ela responde uma segunda coisa: QUANTOS levar. Quase todas levam tudo que esta
 * esperando, porque o custo fixo ja foi pago e deixar item para tras so adia a proxima
 * gravacao. A que nao leva tudo e a que nao agrupa, e e ela que serve de referencia.
 *
 * <h2>O numero de parametros e o assunto</h2>
 *
 * <p>Tres das estrategias daqui tem parametro para ajustar e uma nao tem. A tabela do
 * parametro mostra que as ajustadas so sao boas na carga em que foram ajustadas, e a sem
 * parametro e boa em todas.
 */
public interface Estrategia {

    /**
     * Grava agora?
     *
     * @param pendentes      quantos itens estao esperando, sempre pelo menos um
     * @param esperaDoMaisVelho ha quanto tempo o item mais antigo esta esperando
     */
    boolean gravarAgora(int pendentes, long esperaDoMaisVelho);

    /**
     * Quantos itens entram nesta gravacao.
     *
     * <p>O padrao e TODOS, e e o certo para qualquer estrategia que agrupe: o custo fixo
     * ja foi pago. Quem leva menos e so a estrategia que existe para nao agrupar.
     */
    default int quantosLevar(int pendentes) {
        return pendentes;
    }

    /**
     * Daqui a quanto tempo vale a pena perguntar de novo, se a resposta agora foi nao.
     *
     * <p>ESTE METODO EXISTE POR CAUSA DE UM BUG. Sem ele, a simulacao so reperguntava
     * quando alguem chegava, e uma estrategia por tempo com o aparelho livre e um item
     * esperando ficava parada ate a proxima chegada: o prazo dela vencia sozinho e ninguem
     * olhava.
     *
     * <p>O padrao e INFINITO, que quer dizer "so uma chegada muda a minha resposta", e e o
     * certo para quem decide por contagem. Quem decide por tempo devolve quanto falta.
     */
    default long esperaAteDecidir(int pendentes, long esperaDoMaisVelho) {
        return Long.MAX_VALUE;
    }

    String nome();

    /** Quantos numeros precisam ser acertados para ela funcionar. */
    int parametros();

    /**
     * UMA GRAVACAO POR ITEM: nunca agrupa.
     *
     * <p>Ela e o juiz deste repositorio e tambem a referencia das duas colunas: a menor
     * latencia possivel e o maior numero de gravacoes possivel. Toda outra estrategia e
     * lida contra ela.
     *
     * <p>E ela nao e uma versao de palha: num sistema com pouco transito ela e a resposta
     * certa, porque nao ha com quem agrupar.
     */
    record UmPorUm() implements Estrategia {

        @Override
        public boolean gravarAgora(int pendentes, long esperaDoMaisVelho) {
            return true;
        }

        /*
         * UM ITEM POR GRAVACAO, E E SO ISSO QUE A DISTINGUE DA ADAPTATIVA. As duas gravam
         * sempre que podem; esta deixa os outros para a proxima e paga o custo fixo de novo
         * em cada um.
         */
        @Override
        public int quantosLevar(int pendentes) {
            return 1;
        }

        @Override
        public String nome() {
            return "um por um";
        }

        @Override
        public int parametros() {
            return 0;
        }
    }

    /**
     * POR CONTAGEM: espera juntar tantos itens.
     *
     * <p>E a primeira ideia que qualquer um tem, e ela tem um defeito que nao e de
     * desempenho: quando o transito para com o lote pela metade, os itens que estao la
     * NUNCA sao gravados. A tabela da parada mede isso como itens perdidos, e nao como
     * latencia alta.
     */
    record PorContagem(int quantos) implements Estrategia {

        public PorContagem {
            if (quantos < 1) throw new IllegalArgumentException("pelo menos um item por lote");
        }

        @Override
        public boolean gravarAgora(int pendentes, long esperaDoMaisVelho) {
            return pendentes >= quantos;
        }

        @Override
        public String nome() {
            return "por contagem de " + quantos;
        }

        @Override
        public int parametros() {
            return 1;
        }
    }

    /**
     * POR TEMPO: grava quando o item mais velho ja esperou demais.
     *
     * <p>Ela nao perde item nenhum, porque o tempo passa sozinho. O que ela cobra e uma
     * latencia minima: mesmo com o aparelho livre e um item so, ele espera o prazo inteiro
     * antes de ser gravado.
     */
    record PorTempo(long prazo) implements Estrategia {

        public PorTempo {
            if (prazo < 0) throw new IllegalArgumentException("prazo nao negativo");
        }

        @Override
        public boolean gravarAgora(int pendentes, long esperaDoMaisVelho) {
            return esperaDoMaisVelho >= prazo;
        }

        @Override
        public long esperaAteDecidir(int pendentes, long esperaDoMaisVelho) {
            return Math.max(0, prazo - esperaDoMaisVelho);
        }

        @Override
        public String nome() {
            return "por tempo de " + prazo;
        }

        @Override
        public int parametros() {
            return 1;
        }
    }

    /**
     * POR CONTAGEM COM PRAZO: o conserto obvio da contagem, e ele custa um parametro a mais.
     *
     * <p>Grava quando junta o lote OU quando o mais velho espera demais. Ela e correta e e
     * boa, e precisa de dois numeros acertados para isso.
     */
    record PorContagemComPrazo(int quantos, long prazo) implements Estrategia {

        public PorContagemComPrazo {
            if (quantos < 1) throw new IllegalArgumentException("pelo menos um item por lote");
            if (prazo < 0) throw new IllegalArgumentException("prazo nao negativo");
        }

        @Override
        public boolean gravarAgora(int pendentes, long esperaDoMaisVelho) {
            return pendentes >= quantos || esperaDoMaisVelho >= prazo;
        }

        @Override
        public long esperaAteDecidir(int pendentes, long esperaDoMaisVelho) {
            return Math.max(0, prazo - esperaDoMaisVelho);
        }

        @Override
        public String nome() {
            return "contagem " + quantos + " com prazo " + prazo;
        }

        @Override
        public int parametros() {
            return 2;
        }
    }

    /**
     * ADAPTATIVA: grava sempre que o aparelho esta livre e ha alguem esperando.
     *
     * <p>Ela nao tem parametro nenhum e nunca espera de proposito. O agrupamento dela
     * acontece sozinho: enquanto uma gravacao esta em curso, os itens que chegam se
     * acumulam, e a proxima gravacao leva todos eles.
     *
     * <p>Com pouco transito, cada gravacao leva um item e a latencia e a minima possivel.
     * Com muito transito, o lote cresce exatamente na medida em que o aparelho nao da
     * conta, e as gravacoes por item caem sozinhas.
     *
     * <p>E esse e o nome de verdade dela: COMMIT EM GRUPO. Ela e o que os bancos de dados
     * fazem, e a razao e que ela nao precisa saber nada sobre a carga.
     */
    record Adaptativa() implements Estrategia {

        @Override
        public boolean gravarAgora(int pendentes, long esperaDoMaisVelho) {
            return true;
        }

        @Override
        public String nome() {
            return "adaptativa";
        }

        @Override
        public int parametros() {
            return 0;
        }
    }
}
