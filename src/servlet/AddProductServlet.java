package servlet;

import util.ProductFileHandler;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/addProduct")
public class AddProductServlet extends HttpServlet {
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

        // CSV Format: ID,Name,Category,Price,Stock,Description,ImageUrl
        String record = id + "," + name + "," + category + "," + price + "," + stock + "," + description + "," + imageUrl;

        ProductFileHandler.saveProductToFile(record);
        response.sendRedirect("products");
    }
}