public class Exemplo01 {

    public static void main(String[] args) {
        String produto = "Caneta";
        int quantidade = 3;
        double precoUnitario = 2.50;
        boolean emPromocao = true;

        double total = quantidade * precoUnitario;

        System.out.println("Produto: " + produto);
        System.out.println("Total: R$ " + total);
        System.out.println("Em promoção? " + emPromocao);
        System.out.println("Quantidade em texto: " + quantidade);

    }

}
