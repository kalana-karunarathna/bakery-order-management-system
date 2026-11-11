import util.ProductFileHandler;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- Testing Product File Handler ---");

        List<String> products = ProductFileHandler.getAllProductsFromFile();

        for (String p : products) {
            System.out.println(p);
        }
    }
}
