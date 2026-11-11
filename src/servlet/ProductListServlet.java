package servlet;

import util.ProductFileHandler;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/products")
public class ProductListServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<String> productList = ProductFileHandler.getAllProductsFromFile();
        request.setAttribute("products", productList);
        request.getRequestDispatcher("productList.jsp").forward(request, response);
    }
}
