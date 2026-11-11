package servlet;

import util.ProductFileHandler;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/editProduct")
public class EditProductServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String id = request.getParameter("id");
        String name = request.getParameter("name");
        String category = request.getParameter("category");
        String price = request.getParameter("price");
        String stock = request.getParameter("stock");
        String description = request.getParameter("description");
        String imageUrl = request.getParameter("imageUrl");

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = "";
        }

        List<String> products = ProductFileHandler.getAllProductsFromFile();

        // Update the matching product line
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).startsWith(id + ",")) {
                String updatedRecord = id + "," + name + "," + category + "," + price + "," + stock + "," + description + "," + imageUrl;
                products.set(i, updatedRecord);
                break;
            }
        }

        ProductFileHandler.saveAllProductsToFile(products);
        response.sendRedirect("products");
    }
}