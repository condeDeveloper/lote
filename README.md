# lote

Agrupamento de gravações do zero em Java 21: cinco estratégias para decidir quando gravar, e
o que acontece quando a comparação entre elas depende de um peso que ninguém declarou.

O juiz é a estratégia que grava **um por um**. Ela é o juiz certo e não só o conveniente: não
há nela lote a juntar, prazo a vencer nem decisão a tomar. Chegou, grava. E é ela que dá a
menor latência possível e o maior número de gravações possível, que são os dois extremos
contra os quais todo o resto é lido.

E ele exige **validade, não desempenho**: que todo item seja gravado, exatamente uma vez, e
que nenhum fique durável antes de a gravação acabar. Essas três coisas não têm nada a ver com
quantas gravações foram gastas.

São **duas** unidades e elas andam ao contrário. **Gravações por item**: 1,0 é não agrupar
nada, 0,01 é cem itens por gravação. E **latência em custos fixos**: 1,0 é o mínimo possível,
porque ninguém fica durável antes de o aparelho confirmar.

## A adaptativa agrupa sozinha

```
$ mvn exec:java -Dexec.args="carga"

     intervalo estrategia                 gravacoes por item   itens por gravacao     ocupacao   latencia media
          5000 um por um                              1.0000                 1.00       0.2003             1.13
          5000 adaptativa                             0.9820                 1.02       0.1967             1.11
           200 um por um                              1.0000                 1.00       1.0000         40384.08
           200 adaptativa                             0.1915                 5.22       0.9987             1.59
            15 um por um                              1.0000                 1.00       1.0000         49740.37
            15 adaptativa                             0.0052               192.68       1.0000             4.43
```

A adaptativa não tem parâmetro nenhum e **nunca espera de propósito**: ela grava sempre que o
aparelho está livre e há alguém esperando. O agrupamento acontece sozinho, porque enquanto uma
gravação está em curso os itens que chegam se acumulam.

Com trânsito ralo ela grava **um por um**, igual ao juiz, e a latência dela é a mínima. Com
trânsito pesado o lote chega a **192 itens**, exatamente na medida em que o aparelho não dá
conta.

E o juiz colapsa: com chegadas a cada 200 tiques e 1.010 por gravação, a ocupação vai a 1,0 e a
latência média vai a **40.384 custos fixos**.

## Como rodar

```
mvn test
mvn exec:java -Dexec.args="tudo"
```

Os comandos do medidor são `correcao`, `carga`, `latencia`, `parametro`, `parada` e `tudo`.

## O que as medidas me corrigiram

**A coluna que eu usei para declarar a vencedora escondia uma escolha que eu tinha feito sem
dizer.**

Eu montei uma coluna que soma as duas contas que importam, gravações gastas e latência
sofrida, e comparei todas com a adaptativa. Com ela, a adaptativa ganhava em **todas** as
cargas, e a conclusão parecia limpa.

Só que somar "0,19 gravações por item" com "1,59 custos fixos de latência" é uma escolha de
peso, e eu tinha escolhido um sem perceber. Então pus a mesma conta com a gravação valendo dez
vezes mais:

```
$ mvn exec:java -Dexec.args="parametro"

     intervalo estrategia                param     gravacoes/item       latencia    peso latencia    peso gravacao
          5000 tempo 2000                    1             0.7188           2.73             1.65             0.91
          5000 adaptativa                    0             0.9820           1.11             1.00             1.00
          1000 tempo 2000                    1             0.3354           2.37             1.28             0.66
          1000 adaptativa                    0             0.7306           1.39             1.00             1.00
           200 tempo 2000                    1             0.0917           2.21             1.29             0.89
           200 adaptativa                    0             0.1915           1.59             1.00             1.00
            50 tempo 2000                    1             0.0246           2.44             1.28             1.18
            50 adaptativa                    0             0.0405           1.88             1.00             1.00
```

As duas colunas **discordam**. Com a latência pesando, a adaptativa ganha em cinco de cinco.
Com a gravação pesando, ela perde em **três de cinco**.

Olhe a linha do intervalo 200: a adaptativa gasta 0,19 gravações por item e espera 1,59; a por
tempo gasta 0,09 e espera 2,21. Quem ganha depende inteiramente de quanto uma gravação a mais
incomoda em relação a um tique de espera, e essa é uma pergunta sobre o sistema, não sobre o
algoritmo.

A conclusão honesta não é qual ganha. É que uma coluna só escondia a escolha.

O que continua valendo sem depender de peso nenhum: a adaptativa tem **zero** parâmetros e não
foi ajustada para carga nenhuma, e ela nunca é a pior com peso algum.

## Quando o trânsito para

```
$ mvn exec:java -Dexec.args="parada"

  estrategia                    itens     gravados     perdidos   fracao perdida
  um por um                      1000         1000            0           0.0000
  contagem 32                    1000          992            8           0.0080
  tempo 2000                     1000         1000            0           0.0000
  contagem 32 prazo 2000         1000         1000            0           0.0000
  adaptativa                     1000         1000            0           0.0000
```

A estratégia por contagem espera juntar 32 itens. Quando o trânsito acaba com oito no lote, ela
**nunca** os grava: a condição dela é "juntar tantos" e não vai juntar mais nenhum.

Isso não é uma latência alta que alguém poderia aceitar. É **perda de dado**, e o tamanho dela
não depende do tempo de espera: depende de quantos itens calharam de estar no lote quando tudo
parou. Oito é exatamente o resto de mil por trinta e dois.

O conserto é conhecido e custa um parâmetro a mais, e é por isso que `contagem 32 prazo 2000`
tem dois números para acertar e a adaptativa tem zero.

## O preço do parâmetro

```
$ mvn exec:java -Dexec.args="latencia"

     intervalo estrategia                      media   percentil 99          maior   gravacoes por item
          5000 um por um                        1.13           2.43           4.95               1.0000
          5000 tempo 2000                       2.73           3.03           3.06               0.7188
          5000 adaptativa                       1.11           1.97           2.04               0.9820
```

Com trânsito ralo, a estratégia por tempo faz cada item esperar o prazo inteiro **mesmo com o
aparelho livre**, porque ela não sabe que não há com quem agrupar. São 2,73 custos fixos contra
1,11 da adaptativa, e ela agrupa quase a mesma coisa.

A coluna do percentil 99 existe porque média não é experiência: uma estratégia que agrupa bem
tem média baixa e um item esquecido esperando o lote encher.

## O que não tem aqui

Um aparelho só, sem concorrência de verdade. Uma gravação de cada vez é a hipótese que faz o
agrupamento adaptativo funcionar: ele agrupa exatamente o que chegou enquanto a gravação
anterior estava em curso.

E o custo fixo é constante. Num disco de verdade ele varia com o que mais está acontecendo, e
aí a adaptativa se ajusta sozinha a isso também, que é mais um ponto a favor dela que esta
simulação não mede.

## Licença

MIT.
