import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SistemaRefeicoes {

    // =========================================================================
    // 1. BIBLIOTECA DE MODELOS (Estruturas de Dados Reutilizáveis)
    // =========================================================================

    public static class ItemQuantidade implements Serializable {
        private static final long serialVersionUID = 1L;
        private String nome;
        private String quantidade;

        public ItemQuantidade(String nome, String quantidade) {
            this.nome = nome;
            this.quantidade = quantidade;
        }

        public String getNome() { return nome; }
        public String getQuantidade() { return quantidade; }

        @Override
        public String toString() {
            return quantidade + " de " + nome;
        }
    }

    public static class Refeicao implements Serializable {
        private static final long serialVersionUID = 1L;
        private String tipo;
        private String nome;
        private int vezesNoDia;
        private boolean repetirNaSemana;
        private int vezesNaSemana;
        private List<String> diasDaSemana;
        private List<ItemQuantidade> ingredientes;
        private List<ItemQuantidade> utensilios;
        private String modoPreparo;

        public Refeicao() {
            this.diasDaSemana = new ArrayList<>();
            this.ingredientes = new ArrayList<>();
            this.utensilios = new ArrayList<>();
        }

        // Getters e Setters
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }

        public int getVezesNoDia() { return vezesNoDia; }
        public void setVezesNoDia(int vezesNoDia) { this.vezesNoDia = vezesNoDia; }

        public boolean isRepetirNaSemana() { return repetirNaSemana; }
        public void setRepetirNaSemana(boolean repetirNaSemana) { this.repetirNaSemana = repetirNaSemana; }

        public int getVezesNaSemana() { return vezesNaSemana; }
        public void setVezesNaSemana(int vezesNaSemana) { this.vezesNaSemana = vezesNaSemana; }

        public List<String> getDiasDaSemana() { return diasDaSemana; }
        public void setDiasDaSemana(List<String> diasDaSemana) { this.diasDaSemana = diasDaSemana; }

        public List<ItemQuantidade> getIngredientes() { return ingredientes; }
        public void adicionarIngrediente(ItemQuantidade item) { this.ingredientes.add(item); }

        public List<ItemQuantidade> getUtensilios() { return utensilios; }
        public void adicionarUtensilio(ItemQuantidade item) { this.utensilios.add(item); }

        public String getModoPreparo() { return modoPreparo; }
        public void setModoPreparo(String modoPreparo) { this.modoPreparo = modoPreparo; }
    }

    // =========================================================================
    // 2. BIBLIOTECA DE PERSISTÊNCIA (Armazenamento para Uso Futuro)
    // =========================================================================

    public static class BancoDeDadosRefeicoes {
        private final String caminhoBinario;
        private final String caminhoTexto;

        public BancoDeDadosRefeicoes(String nomeArquivoBase) {
            this.caminhoBinario = nomeArquivoBase + ".dat";
            this.caminhoTexto = nomeArquivoBase + ".txt";
        }

        public void salvarRefeicao(Refeicao novaRefeicao) throws IOException {
            List<Refeicao> todas = carregarTodas();
            todas.add(novaRefeicao);

            // Salva os objetos Java serializados (para importar direto no seu futuro programa)
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(caminhoBinario))) {
                oos.writeObject(todas);
            }

            // Salva também em arquivo de texto legível para consulta humana ou outros sistemas
            salvarArquivoTextoLegivel(todas);
        }

        @SuppressWarnings("unchecked")
        public List<Refeicao> carregarTodas() {
            File arquivo = new File(caminhoBinario);
            if (!arquivo.exists()) {
                return new ArrayList<>();
            }
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(arquivo))) {
                return (List<Refeicao>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Aviso ao carregar banco anterior: " + e.getMessage());
                return new ArrayList<>();
            }
        }

        private void salvarArquivoTextoLegivel(List<Refeicao> lista) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(caminhoTexto), StandardCharsets.UTF_8))) {
                for (Refeicao r : lista) {
                    writer.write("==================================================\n");
                    writer.write("REFEIÇÃO: " + r.getNome().toUpperCase() + " (" + r.getTipo() + ")\n");
                    writer.write("Quantidade de preparos: " + r.getVezesNoDia() + "x\n");
                    if (r.isRepetirNaSemana()) {
                        writer.write("Repetição Semanal: " + r.getVezesNaSemana() + "x (" + String.join(", ", r.getDiasDaSemana()) + ")\n");
                    } else {
                        writer.write("Repetição Semanal: Não\n");
                    }
                    writer.write("\nIngredientes:\n");
                    for (ItemQuantidade i : r.getIngredientes()) {
                        writer.write("  - " + i.getNome() + ": " + i.getQuantidade() + "\n");
                    }
                    writer.write("\nUtensílios:\n");
                    for (ItemQuantidade u : r.getUtensilios()) {
                        writer.write("  - " + u.getNome() + ": " + u.getQuantidade() + "\n");
                    }
                    writer.write("\nModo de Preparo:\n" + r.getModoPreparo() + "\n");
                    writer.write("==================================================\n\n");
                }
            }
        }
    }

    // =========================================================================
    // 3. BIBLIOTECA UTILITÁRIA DE CONSOLE (Entrada Segura e Impressão)
    // =========================================================================

    public static class BibliotecaConsole {
        private final Scanner scanner = new Scanner(System.in);

        public String lerTexto(String mensagem) {
            System.out.print(mensagem);
            return scanner.nextLine().trim();
        }

        public int lerInteiro(String mensagem) {
            while (true) {
                try {
                    System.out.print(mensagem);
                    return Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("  [!] Entrada inválida. Digite apenas números inteiros.");
                }
            }
        }

        public boolean lerConfirmacao(String mensagem) {
            while (true) {
                System.out.print(mensagem + " [S/N]: ");
                String resposta = scanner.nextLine().trim().toUpperCase();
                if (resposta.equals("S") || resposta.equals("SIM")) return true;
                if (resposta.equals("N") || resposta.equals("NAO") || resposta.equals("NÃO")) return false;
                System.out.println("  [!] Responda apenas com S (Sim) ou N (Não).");
            }
        }

        public void exibirRelatorioOrganizado(Refeicao r) {
            System.out.println("\n+-------------------------------------------------------+");
            System.out.printf("| FICHA TÉCNICA DE PREPARO: %-27s |%n", r.getNome().toUpperCase());
            System.out.println("+-------------------------------------------------------+");
            System.out.printf("  • Tipo de Refeição   : %s%n", r.getTipo());
            System.out.printf("  • Qtd. de Preparos   : %d vez(es)%n", r.getVezesNoDia());

            if (r.isRepetirNaSemana()) {
                System.out.printf("  • Repetir na Semana  : Sim (%d vezes)%n", r.getVezesNaSemana());
                System.out.printf("  • Dias da Semana     : %s%n", String.join(", ", r.getDiasDaSemana()));
            } else {
                System.out.println("  • Repetir na Semana  : Não");
            }

            System.out.println("\n  [ INGREDIENTES NECESSÁRIOS ]");
            if (r.getIngredientes().isEmpty()) {
                System.out.println("    (Nenhum ingrediente informado)");
            } else {
                for (ItemQuantidade ing : r.getIngredientes()) {
                    System.out.printf("    - %-25s | Qtd: %s%n", ing.getNome(), ing.getQuantidade());
                }
            }

            System.out.println("\n  [ UTENSÍLIOS UTILIZADOS ]");
            if (r.getUtensilios().isEmpty()) {
                System.out.println("    (Nenhum utensílio informado)");
            } else {
                for (ItemQuantidade ut : r.getUtensilios()) {
                    System.out.printf("    - %-25s | Qtd: %s%n", ut.getNome(), ut.getQuantidade());
                }
            }

            System.out.println("\n  [ MODO DE PREPARO ]");
            String[] linhasPreparo = r.getModoPreparo().split("\n");
            for (String linha : linhasPreparo) {
                System.out.println("    " + linha);
            }
            System.out.println("+-------------------------------------------------------+\n");
        }
    }

    // =========================================================================
    // 4. PROGRAMA PRINCIPAL (Orquestração das Funções)
    // =========================================================================

    private static final BibliotecaConsole console = new BibliotecaConsole();
    private static final BancoDeDadosRefeicoes bancoDados = new BancoDeDadosRefeicoes("dados_refeicoes");

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("       ASSISTENTE DE PREPARO E CADASTRO DE REFEIÇÕES     ");
        System.out.println("=========================================================");

        boolean executando = true;
        while (executando) {
            System.out.println("\nMENU PRINCIPAL:");
            System.out.println("1 - Cadastrar nova refeição");
            System.out.println("2 - Visualizar refeições já salvas no banco");
            System.out.println("3 - Sair");
            
            int opcao = console.lerInteiro("Escolha uma opção: ");

            switch (opcao) {
                case 1:
                    Refeicao novaRefeicao = executarFluxoCadastro();
                    console.exibirRelatorioOrganizado(novaRefeicao);
                    gravarNoBanco(novaRefeicao);
                    break;
                case 2:
                    listarRefeicoesSalvas();
                    break;
                case 3:
                    executando = false;
                    System.out.println("Encerrando o sistema. Seus dados estão salvos em 'dados_refeicoes.dat' e 'dados_refeicoes.txt'.");
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }

    private static Refeicao executarFluxoCadastro() {
        Refeicao refeicao = new Refeicao();

        System.out.println("\n--- 1. IDENTIFICAÇÃO DA REFEIÇÃO ---");
        refeicao.setTipo(console.lerTexto("Qual o tipo de refeição? (Ex: Café da manhã, Almoço, Jantar, Lanche): "));
        refeicao.setNome(console.lerTexto("Qual o nome da refeição que será preparada?: "));
        refeicao.setVezesNoDia(console.lerInteiro("Quantas vezes ela precisará ser feita?: "));

        System.out.println("\n--- 2. PLANEJAMENTO SEMANAL ---");
        boolean repetir = console.lerConfirmacao("Ela será realizada novamente durante a semana?");
        refeicao.setRepetirNaSemana(repetir);

        if (repetir) {
            int vezesSemana = console.lerInteiro("Quantas vezes na semana ela será feita?: ");
            refeicao.setVezesNaSemana(vezesSemana);
            refeicao.setDiasDaSemana(coletarDiasSemana(vezesSemana));
        }

        System.out.println("\n--- 3. INGREDIENTES ---");
        coletarListaDeItens(refeicao, true);

        System.out.println("\n--- 4. UTENSÍLIOS ---");
        coletarListaDeItens(refeicao, false);

        System.out.println("\n--- 5. MODO DE PREPARO ---");
        refeicao.setModoPreparo(coletarPassosPreparo());

        return refeicao;
    }

    private static List<String> coletarDiasSemana(int quantidade) {
        List<String> dias = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            String dia = console.lerTexto("  Informe o " + i + "º dia da semana (Ex: Segunda-feira): ");
            dias.add(dia);
        }
        return dias;
    }

    private static void coletarListaDeItens(Refeicao refeicao, boolean ehIngrediente) {
        String tipoItem = ehIngrediente ? "ingrediente" : "utensílio";
        int totalItens = console.lerInteiro("Quantos " + tipoItem + "s diferentes serão necessários?: ");

        for (int i = 1; i <= totalItens; i++) {
            String nome = console.lerTexto("  Nome do " + tipoItem + " (" + i + "/" + totalItens + "): ");
            String qtd = console.lerTexto("  Quantidade de '" + nome + "' (Ex: 2 unidades, 300g, 1 colher): ");

            ItemQuantidade item = new ItemQuantidade(nome, qtd);
            if (ehIngrediente) {
                refeicao.adicionarIngrediente(item);
            } else {
                refeicao.adicionarUtensilio(item);
            }
        }
    }

    private static String coletarPassosPreparo() {
        System.out.println("Digite cada etapa do preparo e pressione ENTER.");
        System.out.println("Quando terminar, digite apenas a palavra FIM e pressione ENTER:");
        
        StringBuilder preparo = new StringBuilder();
        int numeroPasso = 1;

        while (true) {
            String instrucao = console.lerTexto("  Passo " + numeroPasso + ": ");
            if (instrucao.equalsIgnoreCase("FIM")) {
                break;
            }
            if (!instrucao.isEmpty()) {
                preparo.append(numeroPasso).append(". ").append(instrucao).append("\n");
                numeroPasso++;
            }
        }
        return preparo.length() > 0 ? preparo.toString().trim() : "Modo de preparo não informado.";
    }

    private static void gravarNoBanco(Refeicao refeicao) {
        try {
            bancoDados.salvarRefeicao(refeicao);
            System.out.println("[OK] Cadastro armazenado com sucesso nos arquivos de dados!");
        } catch (IOException e) {
            System.err.println("[ERRO] Não foi possível salvar os dados: " + e.getMessage());
        }
    }

    private static void listarRefeicoesSalvas() {
        List<Refeicao> salvas = bancoDados.carregarTodas();
        if (salvas.isEmpty()) {
            System.out.println("\nNenhuma refeição cadastrada até o momento.");
            return;
        }
        System.out.println("\n=== REFEIÇÕES ARMAZENADAS NO BANCO (" + salvas.size() + ") ===");
        for (Refeicao r : salvas) {
            console.exibirRelatorioOrganizado(r);
        }
    }
}