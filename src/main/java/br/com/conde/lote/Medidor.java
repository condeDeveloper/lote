package br.com.conde.lote;

import java.util.Locale;

/**
 * O MEDIDOR: imprime as tabelas que o README mostra e que os testes conferem.
 *
 * <p>Toda tabela daqui tem um teste do lado que afirma os mesmos numeros. Texto nao quebra
 * quando deixa de ser verdade; teste quebra.
 */
public final class Medidor {

    private Medidor() {
    }

    public static void main(String[] args) {
        String comando = args.length > 0 ? args[0] : "tudo";

        if (comando.equals("correcao") || comando.equals("tudo")) correcao();
        if (comando.equals("carga") || comando.equals("tudo")) carga();
        if (comando.equals("latencia") || comando.equals("tudo")) latencia();
        if (comando.equals("parametro") || comando.equals("tudo")) parametro();
        if (comando.equals("parada") || comando.equals("tudo")) parada();
    }

    private static void correcao() {
        System.out.println();
        System.out.println("TODO ITEM E GRAVADO UMA VEZ SO? UMA DAS ESTRATEGIAS NAO.");
        System.out.println();

        var linhas = Medidas.aCorrecao();
        long erradas = linhas.stream().filter(l -> l.perdidos() > 0).count();

        System.out.printf("  %-24s %-14s %12s %12s %12s%n",
                "estrategia", "chegadas", "esperados", "gravados", "perdidos");

        for (var l : linhas)
            System.out.printf("  %-24s %-14s %12d %12d %12d%n",
                    l.estrategia(), l.chegadas(), l.esperados(), l.gravados(), l.perdidos());

        System.out.println();
        System.out.printf("  %d combinacoes, %d com item perdido.%n", linhas.size(), erradas);
        System.out.println();
    }

    private static void carga() {
        System.out.println();
        System.out.println("GRAVACOES POR ITEM POR CARGA, com " + Medidas.ITENS + " itens.");
        System.out.println("A latencia e em custos fixos de gravacao.");
        System.out.println();
        System.out.printf("  %12s %-24s %20s %20s %12s %16s%n",
                "intervalo", "estrategia", "gravacoes por item", "itens por gravacao",
                "ocupacao", "latencia media");

        for (var l : Medidas.aCarga())
            System.out.printf(Locale.ROOT, "  %12d %-24s %20.4f %20.2f %12.4f %16.2f%n",
                    l.intervalo(), l.estrategia(), l.gravacoesPorItem(), l.itensPorGravacao(),
                    l.ocupacao(), l.latenciaMedia());

        System.out.println();
    }

    private static void latencia() {
        System.out.println();
        System.out.println("A LATENCIA QUE O AGRUPAMENTO ACRESCENTA, em custos fixos.");
        System.out.println();
        System.out.printf("  %12s %-24s %12s %14s %14s %20s%n",
                "intervalo", "estrategia", "media", "percentil 99", "maior",
                "gravacoes por item");

        for (var l : Medidas.aLatencia())
            System.out.printf(Locale.ROOT, "  %12d %-24s %12.2f %14.2f %14.2f %20.4f%n",
                    l.intervalo(), l.estrategia(), l.media(), l.percentil99(), l.maior(),
                    l.gravacoesPorItem());

        System.out.println();
    }

    private static void parametro() {
        System.out.println();
        System.out.println("O PARAMETRO SO SERVE NA CARGA EM QUE FOI AJUSTADO.");
        System.out.println();
        System.out.println("As duas ultimas colunas sao a mesma conta com pesos diferentes,");
        System.out.println("e elas discordam sobre quem ganha.");
        System.out.println();
        System.out.printf("  %12s %-24s %6s %18s %14s %16s %16s%n",
                "intervalo", "estrategia", "param", "gravacoes/item", "latencia",
                "peso latencia", "peso gravacao");

        for (var l : Medidas.oParametro())
            System.out.printf(Locale.ROOT, "  %12d %-24s %6d %18.4f %14.2f %16.2f %16.2f%n",
                    l.intervalo(), l.estrategia(), l.parametros(), l.gravacoesPorItem(),
                    l.latenciaMedia(), l.pesandoALatencia(), l.pesandoAGravacao());

        System.out.println();
    }

    private static void parada() {
        System.out.println();
        System.out.println("O QUE ACONTECE QUANDO O TRANSITO PARA.");
        System.out.println();
        System.out.printf("  %-24s %10s %12s %12s %16s%n",
                "estrategia", "itens", "gravados", "perdidos", "fracao perdida");

        for (var l : Medidas.aParada())
            System.out.printf(Locale.ROOT, "  %-24s %10d %12d %12d %16.4f%n",
                    l.estrategia(), l.itens(), l.gravados(), l.perdidos(), l.fracaoPerdida());

        System.out.println();
    }
}
