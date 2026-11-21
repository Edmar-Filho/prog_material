import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Sistema de cadastro de produtos, controle de estoque e cálculo de lucro (console).
 * Programa único: todas as classes estão neste arquivo.
 *
 * Dados salvos em: produtos.csv e movimentacoes.csv (na pasta de execução).
 *
 * Regras de preço e lucro:
 *  - Cada produto tem um PREÇO DE COMPRA e um PREÇO DE VENDA.
 *  - O preço de venda é fixo: todas as vendas usam o valor cadastrado, até o usuário alterá-lo.
 *  - Lucro de uma venda = (preço de venda - preço de compra) x quantidade vendida.
 *  - Lucro % = lucro / custo x 100.  Ex.: comprado a R$ 1,00 e vendido a R$ 2,00
 *    => lucro de R$ 1,00 por unidade, ou 100%.
 *  - O custo usado em cada venda é o preço de compra vigente no momento da venda
 *    (guardado na movimentação, então vendas antigas não mudam se o preço de compra mudar depois).
 */
public class SistemaEstoque {

    // =====================================================================
    //  CÁLCULO DE PERCENTUAL
    // =====================================================================

    /** Lucro percentual sobre o custo. Retorna null se o custo for zero (não dá para calcular). */
    static BigDecimal percentual(BigDecimal lucro, BigDecimal custo) {
        if (custo.signum() == 0) {
            return null;
        }
        return lucro.multiply(BigDecimal.valueOf(100)).divide(custo, 2, RoundingMode.HALF_UP);
    }

    // =====================================================================
    //  MODELO: tipo de movimentação, produto e movimentação
    // =====================================================================

    enum TipoMovimentacao {
        ENTRADA("Entrada"),
        SAIDA("Saída");

        private final String descricao;

        TipoMovimentacao(String descricao) {
            this.descricao = descricao;
        }

        String getDescricao() {
            return descricao;
        }
    }

    /** Produto cadastrado no estoque. */
    static class Produto {
        private final int codigo;
        private String nome;
        private BigDecimal precoCompra;
        private BigDecimal precoVenda;
        private int quantidade;
        private int estoqueMinimo;

        Produto(int codigo, String nome, BigDecimal precoCompra, BigDecimal precoVenda,
                int quantidade, int estoqueMinimo) {
            this.codigo = codigo;
            this.nome = nome;
            this.precoCompra = precoCompra;
            this.precoVenda = precoVenda;
            this.quantidade = quantidade;
            this.estoqueMinimo = estoqueMinimo;
        }

        int getCodigo() { return codigo; }
        String getNome() { return nome; }
        BigDecimal getPrecoCompra() { return precoCompra; }
        BigDecimal getPrecoVenda() { return precoVenda; }
        int getQuantidade() { return quantidade; }
        int getEstoqueMinimo() { return estoqueMinimo; }

        void setNome(String nome) { this.nome = nome; }
        void setPrecoCompra(BigDecimal precoCompra) { this.precoCompra = precoCompra; }
        void setPrecoVenda(BigDecimal precoVenda) { this.precoVenda = precoVenda; }
        void setEstoqueMinimo(int estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }

        void adicionar(int qtd) { this.quantidade += qtd; }
        void retirar(int qtd) { this.quantidade -= qtd; }

        /** Valor do estoque a preço de compra (custo). */
        BigDecimal getValorEmEstoque() {
            return precoCompra.multiply(BigDecimal.valueOf(quantidade));
        }

        /** Valor do estoque se tudo fosse vendido ao preço de venda. */
        BigDecimal getValorDeVendaEmEstoque() {
            return precoVenda.multiply(BigDecimal.valueOf(quantidade));
        }

        /** Lucro por unidade vendida (venda - compra). */
        BigDecimal getLucroUnitario() {
            return precoVenda.subtract(precoCompra);
        }

        /** Lucro percentual por unidade sobre o custo (null se a compra for R$ 0,00). */
        BigDecimal getLucroPercentual() {
            return percentual(getLucroUnitario(), precoCompra);
        }

        /** Saldo igual ou abaixo do estoque mínimo. */
        boolean estaAbaixoDoMinimo() {
            return quantidade <= estoqueMinimo;
        }

        String toCsv() {
            return codigo + ";" + nome + ";" + precoCompra.toPlainString() + ";"
                    + precoVenda.toPlainString() + ";" + quantidade + ";" + estoqueMinimo;
        }

        static Produto fromCsv(String linha) {
            String[] c = linha.split(";", -1);
            if (c.length == 5) {
                // Formato antigo (um único preço): vira preço de compra e de venda.
                BigDecimal preco = new BigDecimal(c[2]);
                return new Produto(Integer.parseInt(c[0]), c[1], preco, preco,
                        Integer.parseInt(c[3]), Integer.parseInt(c[4]));
            }
            return new Produto(Integer.parseInt(c[0]), c[1], new BigDecimal(c[2]), new BigDecimal(c[3]),
                    Integer.parseInt(c[4]), Integer.parseInt(c[5]));
        }
    }

    /** Registro de uma entrada (compra) ou saída (venda) de estoque. */
    static class Movimentacao {
        private final int codigoProduto;
        private final TipoMovimentacao tipo;
        private final int quantidade;
        private final BigDecimal precoUnitario;   // compra (entrada) ou venda (saída)
        private final BigDecimal custoUnitario;   // preço de compra vigente na época
        private final LocalDate data;

        Movimentacao(int codigoProduto, TipoMovimentacao tipo, int quantidade,
                     BigDecimal precoUnitario, BigDecimal custoUnitario, LocalDate data) {
            this.codigoProduto = codigoProduto;
            this.tipo = tipo;
            this.quantidade = quantidade;
            this.precoUnitario = precoUnitario;
            this.custoUnitario = custoUnitario;
            this.data = data;
        }

        int getCodigoProduto() { return codigoProduto; }
        TipoMovimentacao getTipo() { return tipo; }
        int getQuantidade() { return quantidade; }
        BigDecimal getPrecoUnitario() { return precoUnitario; }
        BigDecimal getCustoUnitario() { return custoUnitario; }
        LocalDate getData() { return data; }

        /** Valor total da movimentação (quantidade x preço unitário). */
        BigDecimal getValorTotal() {
            return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        }

        /** Custo total das unidades movimentadas (quantidade x custo unitário). */
        BigDecimal getCustoTotal() {
            return custoUnitario.multiply(BigDecimal.valueOf(quantidade));
        }

        /** Lucro da venda (só existe para saídas). */
        BigDecimal getLucro() {
            return tipo == TipoMovimentacao.SAIDA ? getValorTotal().subtract(getCustoTotal()) : BigDecimal.ZERO;
        }

        /** Lucro percentual sobre o custo (só para saídas; null se não der para calcular). */
        BigDecimal getLucroPercentual() {
            return tipo == TipoMovimentacao.SAIDA ? percentual(getLucro(), getCustoTotal()) : null;
        }

        String toCsv() {
            return codigoProduto + ";" + tipo.name() + ";" + quantidade + ";"
                    + precoUnitario.toPlainString() + ";" + data + ";" + custoUnitario.toPlainString();
        }

        static Movimentacao fromCsv(String linha) {
            String[] c = linha.split(";", -1);
            BigDecimal preco = new BigDecimal(c[3]);
            // Formato antigo não tinha o custo: assume o mesmo valor (lucro zero nas vendas antigas).
            BigDecimal custo = c.length >= 6 ? new BigDecimal(c[5]) : preco;
            return new Movimentacao(Integer.parseInt(c[0]), TipoMovimentacao.valueOf(c[1]),
                    Integer.parseInt(c[2]), preco, custo, LocalDate.parse(c[4]));
        }
    }

    // =====================================================================
    //  REGRAS DE NEGÓCIO E PERSISTÊNCIA
    // =====================================================================

    static class Estoque {
        private static final Path ARQUIVO_PRODUTOS = Path.of("produtos.csv");
        private static final Path ARQUIVO_MOVIMENTACOES = Path.of("movimentacoes.csv");

        private final List<Produto> produtos = new ArrayList<>();
        private final List<Movimentacao> movimentacoes = new ArrayList<>();
        private int proximoCodigo = 1;

        Estoque() {
            carregar();
        }

        Produto cadastrarProduto(String nome, BigDecimal precoCompra, BigDecimal precoVenda,
                                 int quantidadeInicial, int estoqueMinimo, LocalDate data) {
            nome = limparNome(nome);
            if (nome.isEmpty()) {
                throw new IllegalArgumentException("O nome do produto não pode ser vazio.");
            }
            validarPreco(precoCompra);
            validarPreco(precoVenda);
            if (quantidadeInicial < 0 || estoqueMinimo < 0) {
                throw new IllegalArgumentException("Quantidades não podem ser negativas.");
            }

            Produto produto = new Produto(proximoCodigo++, nome, precoCompra, precoVenda, 0, estoqueMinimo);
            produtos.add(produto);

            // O estoque inicial entra como uma ENTRADA (compra) para ficar registrado no histórico.
            if (quantidadeInicial > 0) {
                aplicarMovimentacao(produto, TipoMovimentacao.ENTRADA, quantidadeInicial,
                        precoCompra, precoCompra, data);
            }
            salvar();
            return produto;
        }

        void alterarProduto(int codigo, String novoNome, BigDecimal novoPrecoCompra,
                            BigDecimal novoPrecoVenda, Integer novoMinimo) {
            Produto p = buscarObrigatorio(codigo);
            if (novoNome != null) {
                String nome = limparNome(novoNome);
                if (nome.isEmpty()) {
                    throw new IllegalArgumentException("O nome do produto não pode ser vazio.");
                }
                p.setNome(nome);
            }
            if (novoPrecoCompra != null) {
                validarPreco(novoPrecoCompra);
                p.setPrecoCompra(novoPrecoCompra);
            }
            if (novoPrecoVenda != null) {
                validarPreco(novoPrecoVenda);
                p.setPrecoVenda(novoPrecoVenda);
            }
            if (novoMinimo != null) {
                if (novoMinimo < 0) {
                    throw new IllegalArgumentException("O estoque mínimo não pode ser negativo.");
                }
                p.setEstoqueMinimo(novoMinimo);
            }
            salvar();
        }

        Produto buscar(int codigo) {
            for (Produto p : produtos) {
                if (p.getCodigo() == codigo) {
                    return p;
                }
            }
            return null;
        }

        List<Produto> listarProdutos() {
            List<Produto> copia = new ArrayList<>(produtos);
            copia.sort(Comparator.comparingInt(Produto::getCodigo));
            return copia;
        }

        /** Valor de todo o estoque a preço de compra. */
        BigDecimal valorTotalEmEstoque() {
            BigDecimal total = BigDecimal.ZERO;
            for (Produto p : produtos) {
                total = total.add(p.getValorEmEstoque());
            }
            return total;
        }

        /** Valor de todo o estoque a preço de venda. */
        BigDecimal valorTotalDeVendaEmEstoque() {
            BigDecimal total = BigDecimal.ZERO;
            for (Produto p : produtos) {
                total = total.add(p.getValorDeVendaEmEstoque());
            }
            return total;
        }

        /**
         * Registra uma ENTRADA (compra). Se atualizarPrecoCadastro for true, o preço de compra
         * do produto passa a ser o informado.
         */
        Movimentacao registrarEntrada(int codigo, int quantidade, BigDecimal precoCompra,
                                      boolean atualizarPrecoCadastro, LocalDate data) {
            Produto produto = buscarObrigatorio(codigo);
            if (quantidade <= 0) {
                throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
            }
            validarPreco(precoCompra);
            if (atualizarPrecoCadastro) {
                produto.setPrecoCompra(precoCompra);
            }
            Movimentacao mov = aplicarMovimentacao(produto, TipoMovimentacao.ENTRADA, quantidade,
                    precoCompra, precoCompra, data);
            salvar();
            return mov;
        }

        /**
         * Registra uma SAÍDA (venda) ao preço de venda cadastrado, que é fixo até ser alterado.
         * O custo usado no lucro é o preço de compra vigente.
         */
        Movimentacao registrarSaida(int codigo, int quantidade, LocalDate data) {
            Produto produto = buscarObrigatorio(codigo);
            if (quantidade <= 0) {
                throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
            }
            if (quantidade > produto.getQuantidade()) {
                throw new IllegalArgumentException("Estoque insuficiente. Saldo atual de '"
                        + produto.getNome() + "': " + produto.getQuantidade());
            }
            Movimentacao mov = aplicarMovimentacao(produto, TipoMovimentacao.SAIDA, quantidade,
                    produto.getPrecoVenda(), produto.getPrecoCompra(), data);
            salvar();
            return mov;
        }

        /** Movimentações de um produto (ou de todos, se codigo for 0), da mais antiga para a mais recente. */
        List<Movimentacao> historico(int codigo) {
            List<Movimentacao> resultado = new ArrayList<>();
            for (Movimentacao m : movimentacoes) {
                if (codigo == 0 || m.getCodigoProduto() == codigo) {
                    resultado.add(m);
                }
            }
            resultado.sort(Comparator.comparing(Movimentacao::getData));
            return resultado;
        }

        /** Movimentações entre duas datas (inclusive). */
        List<Movimentacao> movimentacoesNoPeriodo(LocalDate inicio, LocalDate fim) {
            List<Movimentacao> resultado = new ArrayList<>();
            for (Movimentacao m : historico(0)) {
                if (!m.getData().isBefore(inicio) && !m.getData().isAfter(fim)) {
                    resultado.add(m);
                }
            }
            return resultado;
        }

        private Movimentacao aplicarMovimentacao(Produto produto, TipoMovimentacao tipo, int quantidade,
                                                 BigDecimal precoUnitario, BigDecimal custoUnitario,
                                                 LocalDate data) {
            if (tipo == TipoMovimentacao.ENTRADA) {
                produto.adicionar(quantidade);
            } else {
                produto.retirar(quantidade);
            }
            Movimentacao mov = new Movimentacao(produto.getCodigo(), tipo, quantidade,
                    precoUnitario, custoUnitario, data);
            movimentacoes.add(mov);
            return mov;
        }

        private void validarPreco(BigDecimal preco) {
            if (preco.signum() < 0) {
                throw new IllegalArgumentException("O preço não pode ser negativo.");
            }
        }

        private Produto buscarObrigatorio(int codigo) {
            Produto p = buscar(codigo);
            if (p == null) {
                throw new IllegalArgumentException("Produto de código " + codigo + " não encontrado.");
            }
            return p;
        }

        /** Remove espaços extras e o separador do CSV (;) do nome. */
        private String limparNome(String nome) {
            return nome == null ? "" : nome.replace(";", ",").trim();
        }

        private void carregar() {
            try {
                if (Files.exists(ARQUIVO_PRODUTOS)) {
                    for (String linha : Files.readAllLines(ARQUIVO_PRODUTOS, StandardCharsets.UTF_8)) {
                        if (!linha.isBlank()) {
                            Produto p = Produto.fromCsv(linha);
                            produtos.add(p);
                            proximoCodigo = Math.max(proximoCodigo, p.getCodigo() + 1);
                        }
                    }
                }
                if (Files.exists(ARQUIVO_MOVIMENTACOES)) {
                    for (String linha : Files.readAllLines(ARQUIVO_MOVIMENTACOES, StandardCharsets.UTF_8)) {
                        if (!linha.isBlank()) {
                            movimentacoes.add(Movimentacao.fromCsv(linha));
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("Aviso: não foi possível ler os dados salvos (" + e.getMessage() + ").");
            }
        }

        private void salvar() {
            try {
                List<String> linhasProdutos = new ArrayList<>();
                for (Produto p : produtos) {
                    linhasProdutos.add(p.toCsv());
                }
                List<String> linhasMovs = new ArrayList<>();
                for (Movimentacao m : movimentacoes) {
                    linhasMovs.add(m.toCsv());
                }
                Files.write(ARQUIVO_PRODUTOS, linhasProdutos, StandardCharsets.UTF_8);
                Files.write(ARQUIVO_MOVIMENTACOES, linhasMovs, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("ERRO ao salvar os dados: " + e.getMessage());
            }
        }
    }

    // =====================================================================
    //  INTERFACE (MENU NO CONSOLE)
    // =====================================================================

    private static final Scanner entrada = new Scanner(System.in);
    private static final Estoque estoque = new Estoque();
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public static void main(String[] args) {
        int opcao;
        do {
            exibirMenu();
            opcao = lerInteiro("Escolha uma opção: ", 0, 8);
            System.out.println();
            try {
                switch (opcao) {
                    case 1 -> cadastrarProduto();
                    case 2 -> listarProdutos();
                    case 3 -> alterarProduto();
                    case 4 -> registrarEntrada();
                    case 5 -> registrarSaida();
                    case 6 -> exibirHistorico();
                    case 7 -> relatorioPorPeriodo();
                    case 8 -> alertasEstoqueBaixo();
                    default -> { }
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Erro: " + e.getMessage());
            }
        } while (opcao != 0);
        System.out.println("Sistema encerrado. Dados salvos.");
    }

    private static void exibirMenu() {
        System.out.println();
        System.out.println("=========== CONTROLE DE ESTOQUE ===========");
        System.out.println(" 1 - Cadastrar produto");
        System.out.println(" 2 - Listar produtos, saldo e preços");
        System.out.println(" 3 - Alterar produto (nome, preços, estoque mínimo)");
        System.out.println(" 4 - Registrar ENTRADA de estoque (compra)");
        System.out.println(" 5 - Registrar SAÍDA de estoque (venda)");
        System.out.println(" 6 - Histórico de movimentações");
        System.out.println(" 7 - Relatório por período (vendas e lucro)");
        System.out.println(" 8 - Produtos com estoque baixo");
        System.out.println(" 0 - Sair");
        System.out.println("===========================================");
    }

    private static void cadastrarProduto() {
        System.out.println("--- Cadastro de produto ---");
        String nome = lerTexto("Nome: ");
        BigDecimal compra = lerDecimal("Preço de compra unitário (R$): ");
        BigDecimal venda = lerDecimal("Preço de venda unitário (R$) - fica fixo até você alterar: ");
        if (venda.compareTo(compra) < 0) {
            System.out.println("Atenção: o preço de venda é menor que o de compra (prejuízo em cada venda).");
        }
        int qtd = lerInteiro("Quantidade inicial em estoque (0 se nenhuma): ", 0, Integer.MAX_VALUE);
        int minimo = lerInteiro("Estoque mínimo para alerta (0 se não quiser): ", 0, Integer.MAX_VALUE);
        LocalDate data = LocalDate.now();
        if (qtd > 0) {
            data = lerData("Data da compra inicial (Enter = hoje): ");
        }
        Produto p = estoque.cadastrarProduto(nome, compra, venda, qtd, minimo, data);
        System.out.println("Produto cadastrado com o código " + p.getCodigo() + ".");
        System.out.println("Lucro por unidade: " + dinheiro(p.getLucroUnitario())
                + " (" + percentualTexto(p.getLucroPercentual()) + " sobre o custo).");
    }

    private static void listarProdutos() {
        List<Produto> lista = estoque.listarProdutos();
        if (lista.isEmpty()) {
            System.out.println("Nenhum produto cadastrado.");
            return;
        }
        System.out.printf("%-5s %-22s %11s %11s %9s %7s %6s %14s%n",
                "Cód.", "Produto", "Compra", "Venda", "Lucro %", "Saldo", "Mín.", "Custo estoque");
        System.out.println("-".repeat(92));
        for (Produto p : lista) {
            System.out.printf("%-5d %-22s %11s %11s %9s %7d %6d %14s %s%n",
                    p.getCodigo(), cortar(p.getNome(), 22), dinheiro(p.getPrecoCompra()),
                    dinheiro(p.getPrecoVenda()), percentualTexto(p.getLucroPercentual()),
                    p.getQuantidade(), p.getEstoqueMinimo(), dinheiro(p.getValorEmEstoque()),
                    p.estaAbaixoDoMinimo() ? "<< BAIXO" : "");
        }
        System.out.println("-".repeat(92));
        BigDecimal custo = estoque.valorTotalEmEstoque();
        BigDecimal venda = estoque.valorTotalDeVendaEmEstoque();
        System.out.println("Valor do estoque a preço de compra: " + dinheiro(custo));
        System.out.println("Valor do estoque a preço de venda:  " + dinheiro(venda));
        System.out.println("Lucro previsto se tudo for vendido: " + dinheiro(venda.subtract(custo)));
    }

    private static void alterarProduto() {
        int codigo = lerInteiro("Código do produto: ", 1, Integer.MAX_VALUE);
        Produto p = estoque.buscar(codigo);
        if (p == null) {
            System.out.println("Produto não encontrado.");
            return;
        }
        System.out.println("Produto: " + p.getNome() + " | Compra: " + dinheiro(p.getPrecoCompra())
                + " | Venda: " + dinheiro(p.getPrecoVenda()) + " | Mínimo: " + p.getEstoqueMinimo());
        System.out.println("(Deixe em branco e tecle Enter para manter o valor atual)");
        System.out.println("(O novo preço de venda vale para as próximas vendas; as já feitas não mudam)");

        System.out.print("Novo nome: ");
        String nome = entrada.nextLine().trim();
        System.out.print("Novo preço de compra (R$): ");
        String compraTxt = entrada.nextLine().trim();
        System.out.print("Novo preço de venda (R$): ");
        String vendaTxt = entrada.nextLine().trim();
        System.out.print("Novo estoque mínimo: ");
        String minTxt = entrada.nextLine().trim();

        BigDecimal compra = null;
        BigDecimal venda = null;
        Integer minimo = null;
        try {
            if (!compraTxt.isEmpty()) {
                compra = new BigDecimal(compraTxt.replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
            }
            if (!vendaTxt.isEmpty()) {
                venda = new BigDecimal(vendaTxt.replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
            }
            if (!minTxt.isEmpty()) {
                minimo = Integer.parseInt(minTxt);
            }
        } catch (NumberFormatException e) {
            System.out.println("Valor numérico inválido. Nada foi alterado.");
            return;
        }
        estoque.alterarProduto(codigo, nome.isEmpty() ? null : nome, compra, venda, minimo);
        System.out.println("Produto atualizado.");
        if (p.getPrecoVenda().compareTo(p.getPrecoCompra()) < 0) {
            System.out.println("Atenção: o preço de venda está menor que o de compra (prejuízo em cada venda).");
        }
    }

    private static void registrarEntrada() {
        System.out.println("--- Registrar ENTRADA (compra) ---");
        int codigo = lerInteiro("Código do produto: ", 1, Integer.MAX_VALUE);
        Produto p = estoque.buscar(codigo);
        if (p == null) {
            System.out.println("Produto não encontrado.");
            return;
        }
        System.out.println("Produto: " + p.getNome() + " | Saldo atual: " + p.getQuantidade()
                + " | Preço de compra cadastrado: " + dinheiro(p.getPrecoCompra()));

        int qtd = lerInteiro("Quantidade comprada: ", 1, Integer.MAX_VALUE);
        BigDecimal preco = lerDecimalOpcional(
                "Preço de compra unitário (Enter = " + dinheiro(p.getPrecoCompra()) + "): ", p.getPrecoCompra());
        boolean atualizar = false;
        if (preco.compareTo(p.getPrecoCompra()) != 0) {
            atualizar = lerSimNao("O preço mudou. Atualizar o preço de compra do produto para "
                    + dinheiro(preco) + "? (s/n): ");
        }
        LocalDate data = lerData("Data da compra (dd/mm/aaaa, Enter = hoje): ");

        Movimentacao m = estoque.registrarEntrada(codigo, qtd, preco, atualizar, data);
        System.out.println();
        System.out.println("Entrada registrada em " + FORMATO_DATA.format(m.getData()) + ":");
        System.out.println("  " + m.getQuantidade() + " x " + dinheiro(m.getPrecoUnitario())
                + " = " + dinheiro(m.getValorTotal()));
        System.out.println("  Novo saldo de '" + p.getNome() + "': " + p.getQuantidade());
    }

    private static void registrarSaida() {
        System.out.println("--- Registrar SAÍDA (venda) ---");
        int codigo = lerInteiro("Código do produto: ", 1, Integer.MAX_VALUE);
        Produto p = estoque.buscar(codigo);
        if (p == null) {
            System.out.println("Produto não encontrado.");
            return;
        }
        System.out.println("Produto: " + p.getNome() + " | Saldo atual: " + p.getQuantidade()
                + " | Preço de venda (fixo): " + dinheiro(p.getPrecoVenda())
                + " | Compra: " + dinheiro(p.getPrecoCompra()));

        int qtd = lerInteiro("Quantidade vendida: ", 1, Integer.MAX_VALUE);
        LocalDate data = lerData("Data da venda (dd/mm/aaaa, Enter = hoje): ");

        Movimentacao m = estoque.registrarSaida(codigo, qtd, data);
        System.out.println();
        System.out.println("Venda registrada em " + FORMATO_DATA.format(m.getData()) + ":");
        System.out.println("  Receita: " + m.getQuantidade() + " x " + dinheiro(m.getPrecoUnitario())
                + " = " + dinheiro(m.getValorTotal()));
        System.out.println("  Custo:   " + m.getQuantidade() + " x " + dinheiro(m.getCustoUnitario())
                + " = " + dinheiro(m.getCustoTotal()));
        System.out.println("  LUCRO:   " + dinheiro(m.getLucro())
                + " (" + percentualTexto(m.getLucroPercentual()) + " sobre o custo)");
        System.out.println("  Novo saldo de '" + p.getNome() + "': " + p.getQuantidade());
    }

    private static void exibirHistorico() {
        int codigo = lerInteiro("Código do produto (0 = todos): ", 0, Integer.MAX_VALUE);
        if (codigo != 0 && estoque.buscar(codigo) == null) {
            System.out.println("Produto não encontrado.");
            return;
        }
        imprimirMovimentacoes(estoque.historico(codigo));
    }

    private static void relatorioPorPeriodo() {
        System.out.println("--- Relatório por período ---");
        LocalDate inicio = lerDataObrigatoria("Data inicial (dd/mm/aaaa): ");
        LocalDate fim = lerDataObrigatoria("Data final (dd/mm/aaaa): ");
        if (fim.isBefore(inicio)) {
            System.out.println("A data final não pode ser anterior à inicial.");
            return;
        }
        List<Movimentacao> lista = estoque.movimentacoesNoPeriodo(inicio, fim);
        System.out.println();
        System.out.println("Período: " + FORMATO_DATA.format(inicio) + " a " + FORMATO_DATA.format(fim));
        imprimirMovimentacoes(lista);
        if (lista.isEmpty()) {
            return;
        }

        int qtdCompras = 0;
        int qtdVendas = 0;
        BigDecimal valorCompras = BigDecimal.ZERO;
        BigDecimal receita = BigDecimal.ZERO;
        BigDecimal custoVendas = BigDecimal.ZERO;
        for (Movimentacao m : lista) {
            if (m.getTipo() == TipoMovimentacao.ENTRADA) {
                qtdCompras += m.getQuantidade();
                valorCompras = valorCompras.add(m.getValorTotal());
            } else {
                qtdVendas += m.getQuantidade();
                receita = receita.add(m.getValorTotal());
                custoVendas = custoVendas.add(m.getCustoTotal());
            }
        }
        BigDecimal lucro = receita.subtract(custoVendas);

        System.out.println();
        System.out.println("COMPRAS (entradas): " + qtdCompras + " un. = " + dinheiro(valorCompras));
        System.out.println("VENDAS (saídas):    " + qtdVendas + " un. vendidas");
        System.out.println("  Receita das vendas:            " + dinheiro(receita));
        System.out.println("  Custo das unidades vendidas:   " + dinheiro(custoVendas));
        System.out.println("  LUCRO OBTIDO:                  " + dinheiro(lucro)
                + " (" + percentualTexto(percentual(lucro, custoVendas)) + " sobre o custo)");

        // Lucro por produto
        boolean cabecalho = false;
        for (Produto p : estoque.listarProdutos()) {
            int qtd = 0;
            BigDecimal rec = BigDecimal.ZERO;
            BigDecimal cus = BigDecimal.ZERO;
            for (Movimentacao m : lista) {
                if (m.getCodigoProduto() == p.getCodigo() && m.getTipo() == TipoMovimentacao.SAIDA) {
                    qtd += m.getQuantidade();
                    rec = rec.add(m.getValorTotal());
                    cus = cus.add(m.getCustoTotal());
                }
            }
            if (qtd == 0) {
                continue;
            }
            if (!cabecalho) {
                System.out.println();
                System.out.println("Lucro por produto:");
                System.out.printf("%-5s %-22s %8s %14s %14s %14s %9s%n",
                        "Cód.", "Produto", "Vendidas", "Receita", "Custo", "Lucro", "Lucro %");
                System.out.println("-".repeat(92));
                cabecalho = true;
            }
            BigDecimal lucroProduto = rec.subtract(cus);
            System.out.printf("%-5d %-22s %8d %14s %14s %14s %9s%n",
                    p.getCodigo(), cortar(p.getNome(), 22), qtd, dinheiro(rec), dinheiro(cus),
                    dinheiro(lucroProduto), percentualTexto(percentual(lucroProduto, cus)));
        }
    }

    private static void alertasEstoqueBaixo() {
        boolean achou = false;
        for (Produto p : estoque.listarProdutos()) {
            if (p.estaAbaixoDoMinimo()) {
                if (!achou) {
                    System.out.println("Produtos com saldo igual ou abaixo do mínimo:");
                    achou = true;
                }
                System.out.printf("  [%d] %s - saldo %d (mínimo %d)%n",
                        p.getCodigo(), p.getNome(), p.getQuantidade(), p.getEstoqueMinimo());
            }
        }
        if (!achou) {
            System.out.println("Nenhum produto com estoque baixo.");
        }
    }

    // ----- Impressão -----

    private static void imprimirMovimentacoes(List<Movimentacao> lista) {
        if (lista.isEmpty()) {
            System.out.println("Nenhuma movimentação encontrada.");
            return;
        }
        System.out.printf("%-11s %-8s %-5s %-20s %6s %12s %13s %13s%n",
                "Data", "Tipo", "Cód.", "Produto", "Qtd", "Preço unit.", "Total", "Lucro");
        System.out.println("-".repeat(95));
        for (Movimentacao m : lista) {
            Produto p = estoque.buscar(m.getCodigoProduto());
            String nome = p != null ? p.getNome() : "(removido)";
            String lucro = m.getTipo() == TipoMovimentacao.SAIDA ? dinheiro(m.getLucro()) : "-";
            System.out.printf("%-11s %-8s %-5d %-20s %6d %12s %13s %13s%n",
                    FORMATO_DATA.format(m.getData()), m.getTipo().getDescricao(), m.getCodigoProduto(),
                    cortar(nome, 20), m.getQuantidade(), dinheiro(m.getPrecoUnitario()),
                    dinheiro(m.getValorTotal()), lucro);
        }
    }

    private static String dinheiro(BigDecimal valor) {
        return "R$ " + String.format(PT_BR, "%,.2f", valor);
    }

    private static String percentualTexto(BigDecimal percentual) {
        return percentual == null ? "n/d" : String.format(PT_BR, "%,.2f%%", percentual);
    }

    private static String cortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + ".";
    }

    // ----- Leitura de dados -----

    private static String lerTexto(String mensagem) {
        while (true) {
            System.out.print(mensagem);
            String texto = entrada.nextLine().trim();
            if (!texto.isEmpty()) {
                return texto;
            }
            System.out.println("Este campo não pode ficar vazio.");
        }
    }

    private static int lerInteiro(String mensagem, int min, int max) {
        while (true) {
            System.out.print(mensagem);
            String texto = entrada.nextLine().trim();
            try {
                int valor = Integer.parseInt(texto);
                if (valor >= min && valor <= max) {
                    return valor;
                }
                System.out.println("Valor fora do intervalo permitido.");
            } catch (NumberFormatException e) {
                System.out.println("Digite um número inteiro válido.");
            }
        }
    }

    private static boolean lerSimNao(String mensagem) {
        while (true) {
            System.out.print(mensagem);
            String texto = entrada.nextLine().trim().toLowerCase();
            if (texto.equals("s") || texto.equals("sim")) {
                return true;
            }
            if (texto.equals("n") || texto.equals("nao") || texto.equals("não")) {
                return false;
            }
            System.out.println("Responda com s ou n.");
        }
    }

    private static BigDecimal lerDecimal(String mensagem) {
        while (true) {
            System.out.print(mensagem);
            BigDecimal valor = converterDecimal(entrada.nextLine());
            if (valor != null) {
                return valor;
            }
        }
    }

    /** Se o usuário apenas teclar Enter, retorna o valor padrão. */
    private static BigDecimal lerDecimalOpcional(String mensagem, BigDecimal padrao) {
        while (true) {
            System.out.print(mensagem);
            String texto = entrada.nextLine();
            if (texto.isBlank()) {
                return padrao;
            }
            BigDecimal valor = converterDecimal(texto);
            if (valor != null) {
                return valor;
            }
        }
    }

    private static BigDecimal converterDecimal(String texto) {
        try {
            BigDecimal valor = new BigDecimal(texto.trim().replace(",", "."));
            if (valor.signum() < 0) {
                System.out.println("O valor não pode ser negativo.");
                return null;
            }
            return valor.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            System.out.println("Digite um valor válido (ex.: 12,50).");
            return null;
        }
    }

    /** Lê uma data; se o usuário apenas teclar Enter, usa a data de hoje. */
    private static LocalDate lerData(String mensagem) {
        while (true) {
            System.out.print(mensagem);
            String texto = entrada.nextLine().trim();
            if (texto.isEmpty()) {
                return LocalDate.now();
            }
            try {
                return LocalDate.parse(texto, FORMATO_DATA);
            } catch (DateTimeParseException e) {
                System.out.println("Data inválida. Use o formato dd/mm/aaaa.");
            }
        }
    }

    private static LocalDate lerDataObrigatoria(String mensagem) {
        while (true) {
            System.out.print(mensagem);
            String texto = entrada.nextLine().trim();
            try {
                return LocalDate.parse(texto, FORMATO_DATA);
            } catch (DateTimeParseException e) {
                System.out.println("Data inválida. Use o formato dd/mm/aaaa.");
            }
        }
    }
}
