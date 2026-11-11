package servlet;

import util.ProductFileHandler;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/editCategory")
public class EditCategoryServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String oldCategory = request.getParameter("oldCategory");
        String newCategory = request.getParameter("newCategory");

        if (oldCategory != null && newCategory != null && !newCategory.trim().isEmpty()) {
            List<String> products = ProductFileHandler.getAllProductsFromFile();

            for (int i = 0; i < products.size(); i++) {
                String[] data = products.get(i).split(",");
                if (data.length > 2 && data[2].trim().equalsIgnoreCase(oldCategory.trim())) {
                    data[2] = newCategory.trim();
                    products.set(i, String.join(",", data));
                }
            }
            ProductFileHandler.saveAllProductsToFile(products);
        }
        response.sendRedirect("products");
    }
}