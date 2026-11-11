<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sweet Delights Bakery - Categories & Menu</title>

    <!-- Bootstrap 5 CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <!-- FontAwesome Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,600;0,800;1,600&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">

    <style>
        :root {
            --primary-color: #6B4226;
            --secondary-color: #FFF8F0;
            --accent-color: #D9A066;
            --bg-color: #FAF4EE;
            --text-dark: #2D2926;
            --text-muted: #746A62;
            --border-color: #E8DDD1;
            --success-color: #326442;
            --error-color: #A23732;
            --warning-color: #815D17;
        }

        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: var(--bg-color);
            background-image:
                radial-gradient(at 0% 0%, rgba(217, 160, 102, 0.1) 0px, transparent 50%),
                radial-gradient(at 100% 100%, rgba(107, 66, 38, 0.08) 0px, transparent 50%);
            background-attachment: fixed;
            color: var(--text-dark);
        }

        .navbar-custom {
            background-color: #ffffff;
            border-bottom: 1px solid var(--border-color);
            padding: 15px 0;
            box-shadow: 0 2px 10px rgba(107, 66, 38, 0.05);
        }

        .brand-logo {
            font-weight: 800;
            font-size: 1.5rem;
            color: var(--primary-color);
            text-decoration: none;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .hero-banner {
            background: linear-gradient(rgba(45, 41, 38, 0.6), rgba(107, 66, 38, 0.65)),
                        url('https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=1200&q=80') center/cover no-repeat;
            height: 170px;
            border-radius: 20px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--secondary-color);
            text-align: center;
            margin-bottom: 25px;
            box-shadow: 0 10px 25px rgba(107, 66, 38, 0.15);
        }

        .hero-banner h1 {
            font-family: 'Playfair Display', serif;
            font-weight: 800;
            font-size: 2.8rem;
            letter-spacing: 0.5px;
            color: #FFF8F0;
            text-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
            margin: 0;
        }

        @keyframes fadeInUp {
            from { opacity: 0; transform: translateY(20px); }
            to { opacity: 1; transform: translateY(0); }
        }

        @keyframes cartBadgePop {
            0% { transform: translate(-50%, -50%) scale(1); }
            50% { transform: translate(-50%, -50%) scale(1.4); }
            100% { transform: translate(-50%, -50%) scale(1); }
        }

        .badge-pop { animation: cartBadgePop 0.35s ease-in-out; }

        .category-card {
            border: 1px solid var(--border-color);
            border-radius: 20px;
            background: #ffffff;
            overflow: hidden;
            cursor: pointer;
            position: relative !important;
            height: 260px !important;
            display: flex;
            flex-direction: column;
            justify-content: flex-end;
            padding: 20px;
            box-shadow: 0 8px 22px rgba(107, 66, 38, 0.08);
            animation: fadeInUp 0.5s cubic-bezier(0.25, 0.8, 0.25, 1) forwards;
            transition: all 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
        }

        .category-card:hover {
            transform: translateY(-8px) scale(1.01);
            box-shadow: 0 20px 40px rgba(107, 66, 38, 0.22);
            border-color: var(--accent-color);
        }

        .category-card.unavailable-card { opacity: 0.55; filter: grayscale(70%); }

        .category-bg-img {
            position: absolute !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            object-fit: cover !important;
            transition: transform 0.6s cubic-bezier(0.25, 1, 0.5, 1) !important;
            z-index: 1;
        }

        .category-card:hover .category-bg-img { transform: scale(1.12); }

        .category-overlay {
            position: absolute !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            background: linear-gradient(to top, rgba(45, 41, 38, 0.88) 0%, rgba(45, 41, 38, 0.2) 60%, rgba(0,0,0,0) 100%) !important;
            z-index: 2;
        }

        .category-content { position: relative !important; z-index: 3 !important; color: #ffffff !important; }

        .food-card {
            border: 1px solid var(--border-color);
            border-radius: 18px;
            background: #ffffff;
            overflow: hidden;
            height: 100%;
            display: flex;
            flex-direction: column;
            box-shadow: 0 8px 22px rgba(107, 66, 38, 0.07);
            animation: fadeInUp 0.5s cubic-bezier(0.25, 0.8, 0.25, 1) forwards;
            transition: all 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
        }

        .food-card:hover {
            transform: translateY(-8px) scale(1.01);
            box-shadow: 0 18px 38px rgba(107, 66, 38, 0.18);
            border-color: var(--accent-color);
        }

        .food-img-container {
            position: relative;
            height: 180px;
            overflow: hidden;
            background-color: var(--secondary-color);
        }

        .food-img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            transition: transform 0.6s cubic-bezier(0.25, 1, 0.5, 1) !important;
        }

        .food-card:hover .food-img { transform: scale(1.1); }

        .stock-badge-in { background-color: #E8F5E9; color: var(--success-color); border: 1px solid #C8E6C9; }
        .stock-badge-low { background-color: #FFF8E1; color: var(--warning-color); border: 1px solid #FFECB3; }
        .stock-badge-out { background-color: #FFEBEE; color: var(--error-color); border: 1px solid #FFCDD2; }

        .stock-badge-general { font-size: 0.8rem; font-weight: 700; padding: 5px 12px; border-radius: 8px; }

        .price-tag { font-size: 1.25rem; font-weight: 800; color: var(--primary-color); }

        .btn-theme-action, .btn-search {
            transition: all 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275) !important;
        }

        .btn-theme-action {
            background-color: var(--primary-color);
            color: var(--secondary-color);
            font-weight: 700;
            padding: 9px 20px;
            border-radius: 12px;
            border: none;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 8px;
        }

        .btn-theme-action:hover {
            background-color: #52321C;
            color: #ffffff;
            transform: translateY(-2px) scale(1.02);
            box-shadow: 0 8px 18px rgba(107, 66, 38, 0.22);
        }

        .search-box-container {
            border: 1px solid var(--border-color);
            border-radius: 50px;
            background: #ffffff;
            padding: 5px 8px 5px 20px;
            box-shadow: 0 6px 18px rgba(107, 66, 38, 0.06);
            transition: all 0.3s ease;
        }

        .search-box-container:focus-within {
            border-color: var(--accent-color);
            box-shadow: 0 8px 25px rgba(107, 66, 38, 0.12);
        }

        .search-input {
            border: none;
            outline: none;
            box-shadow: none;
            font-size: 1rem;
            color: var(--text-dark);
            background: transparent;
        }

        .btn-search {
            background-color: var(--primary-color);
            color: #ffffff;
            border-radius: 40px;
            padding: 8px 22px;
            font-weight: 600;
            border: none;
        }

        .click-to-cart-bar {
            background-color: var(--secondary-color);
            color: var(--primary-color);
            border: 1px dashed var(--accent-color);
            font-weight: 700;
            border-radius: 10px;
            padding: 10px;
            text-align: center;
            cursor: pointer;
            transition: all 0.25s ease;
        }

        .click-to-cart-bar:hover {
            background-color: var(--primary-color);
            color: #ffffff;
            transform: translateY(-2px);
            box-shadow: 0 6px 15px rgba(107, 66, 38, 0.15);
        }

        .btn-delete-cart-item {
            color: var(--error-color);
            background: #FFEBEE;
            border: 1px solid #FFCDD2;
            padding: 6px 10px;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.2s;
        }

        .btn-delete-cart-item:hover {
            background-color: var(--error-color);
            color: #ffffff;
        }

        .cart-qty-btn {
            width: 30px;
            height: 30px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 6px;
            border: 1px solid var(--border-color);
            background-color: #ffffff;
            font-weight: bold;
            color: var(--primary-color);
            transition: all 0.2s;
        }

        .cart-qty-btn:hover {
            background-color: var(--primary-color);
            color: #ffffff;
        }
    </style>
</head>
<body>

    <!-- Header Navbar -->
    <nav class="navbar navbar-custom sticky-top mb-4">
        <div class="container d-flex justify-content-between align-items-center">
            <a class="brand-logo" href="products">
                <i class="fa-solid fa-cookie-bite"></i> Sweet Delights Bakery
            </a>

            <button class="btn btn-theme-action position-relative" type="button" data-bs-toggle="offcanvas" data-bs-target="#cartOffcanvas">
                <i class="fa-solid fa-basket-shopping"></i> My Cart
                <span id="cartBadgeCount" class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">0</span>
            </button>
        </div>
    </nav>

    <div class="container pb-5">

        <div class="hero-banner">
            <h1>Freshly Baked Daily</h1>
        </div>

        <!-- SEARCH BAR SECTION -->
        <div class="row justify-content-center mb-5">
            <div class="col-md-9 col-lg-7">
                <div class="search-box-container d-flex align-items-center gap-2">
                    <i class="fa-solid fa-magnifying-glass text-muted ms-2"></i>
                    <input type="text" id="searchInput" class="form-control search-input" placeholder="Search items (e.g. Fish Bun, Cake, Coffee)..." onkeypress="handleKeyPress(event)">
                    <button class="btn btn-search" type="button" onclick="performSearch()">
                        <i class="fa-solid fa-magnifying-glass me-1"></i> Search
                    </button>
                </div>
            </div>
        </div>

        <%
            List<String> products = (List<String>) request.getAttribute("products");
            Map<String, Integer> categoryCounts = new HashMap<>();

            if (products != null) {
                for (String p : products) {
                    String[] data = p.split(",");
                    if (data.length > 2) {
                        String cat = data[2].trim().toUpperCase();
                        categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);
                    }
                }
            }
        %>

        <!-- 1. CATEGORIES SECTION -->
        <div id="categoryViewSection">
            <div class="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h3 class="fw-bold m-0" style="color: var(--primary-color);"><i class="fa-solid fa-layer-group me-2"></i> Explore Categories</h3>
                    <p class="text-muted small m-0 mt-1">Select a category to explore items</p>
                </div>
                <a href="addProduct.jsp" class="btn-theme-action">
                    <i class="fa-solid fa-folder-plus"></i> Add Category
                </a>
            </div>

            <div class="row row-cols-1 row-cols-md-2 row-cols-lg-4 g-4 mb-5">
                <!-- Cake -->
                <div class="col">
                    <div class="category-card" id="card-CAKE" onclick="handleCategoryClick('CAKE', 'Cakes Collection 🍰')">
                        <img id="img-CAKE" src="https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80" class="category-bg-img" alt="Cakes">
                        <div class="category-overlay"></div>
                        <div class="category-content">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="badge bg-warning text-dark" id="badge-CAKE"><%= categoryCounts.getOrDefault("CAKE", 0) %> Items</span>
                                <div class="category-actions" onclick="event.stopPropagation();">
                                    <a href="editCategory.jsp?name=Cake" class="btn btn-sm btn-light text-dark py-1 px-2 rounded-2 fw-semibold shadow-sm" title="Edit Category">
                                        <i class="fa-solid fa-pen me-1"></i> Edit
                                    </a>
                                </div>
                            </div>
                            <h3 class="fw-bold m-0" id="title-CAKE">Cakes 🍰</h3>
                            <p class="small text-light m-0" id="desc-CAKE">Delicious, custom & fresh cakes</p>
                        </div>
                    </div>
                </div>

                <!-- Pastry -->
                <div class="col">
                    <div class="category-card" id="card-PASTRY" onclick="handleCategoryClick('PASTRY', 'Pastries Collection 🥐')">
                        <img id="img-PASTRY" src="https://images.unsplash.com/photo-1555507036-ab1f4038808a?auto=format&fit=crop&w=600&q=80" class="category-bg-img" alt="Pastries">
                        <div class="category-overlay"></div>
                        <div class="category-content">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="badge bg-warning text-dark" id="badge-PASTRY"><%= categoryCounts.getOrDefault("PASTRY", 0) %> Items</span>
                                <div class="category-actions" onclick="event.stopPropagation();">
                                    <a href="editCategory.jsp?name=Pastry" class="btn btn-sm btn-light text-dark py-1 px-2 rounded-2 fw-semibold shadow-sm" title="Edit Category">
                                        <i class="fa-solid fa-pen me-1"></i> Edit
                                    </a>
                                </div>
                            </div>
                            <h3 class="fw-bold m-0" id="title-PASTRY">Pastries 🥐</h3>
                            <p class="small text-light m-0" id="desc-PASTRY">Crispy, rolls & savory pastries</p>
                        </div>
                    </div>
                </div>

                <!-- Bread -->
                <div class="col">
                    <div class="category-card" id="card-BREAD" onclick="handleCategoryClick('BREAD', 'Breads Collection 🍞')">
                        <img id="img-BREAD" src="https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=600&q=80" class="category-bg-img" alt="Breads">
                        <div class="category-overlay"></div>
                        <div class="category-content">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="badge bg-warning text-dark" id="badge-BREAD"><%= categoryCounts.getOrDefault("BREAD", 0) %> Items</span>
                                <div class="category-actions" onclick="event.stopPropagation();">
                                    <a href="editCategory.jsp?name=Bread" class="btn btn-sm btn-light text-dark py-1 px-2 rounded-2 fw-semibold shadow-sm" title="Edit Category">
                                        <i class="fa-solid fa-pen me-1"></i> Edit
                                    </a>
                                </div>
                            </div>
                            <h3 class="fw-bold m-0" id="title-BREAD">Breads 🍞</h3>
                            <p class="small text-light m-0" id="desc-BREAD">Freshly baked daily breads</p>
                        </div>
                    </div>
                </div>

                <!-- Drinks -->
                <div class="col">
                    <div class="category-card" id="card-DRINKS" onclick="handleCategoryClick('DRINKS', 'Drinks & Beverages 🧃')">
                        <img id="img-DRINKS" src="https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80" class="category-bg-img" alt="Drinks">
                        <div class="category-overlay"></div>
                        <div class="category-content">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="badge bg-warning text-dark" id="badge-DRINKS"><%= categoryCounts.getOrDefault("DRINKS", 0) %> Items</span>
                                <div class="category-actions" onclick="event.stopPropagation();">
                                    <a href="editCategory.jsp?name=Drinks" class="btn btn-sm btn-light text-dark py-1 px-2 rounded-2 fw-semibold shadow-sm" title="Edit Category">
                                        <i class="fa-solid fa-pen me-1"></i> Edit
                                    </a>
                                </div>
                            </div>
                            <h3 class="fw-bold m-0" id="title-DRINKS">Drinks 🧃</h3>
                            <p class="small text-light m-0" id="desc-DRINKS">Fresh juices, coffee & beverages</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 2. PRODUCTS SECTION -->
        <div id="productViewSection" style="display: none;">
            <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-3">
                <div class="d-flex align-items-center gap-3">
                    <button class="btn rounded-circle shadow-sm" style="background-color: var(--secondary-color); color: var(--primary-color); border: 1px solid var(--border-color);" onclick="showAllCategories()">
                        <i class="fa-solid fa-arrow-left"></i>
                    </button>
                    <h3 class="fw-bold m-0" id="selectedCategoryTitle" style="color: var(--primary-color);">Selected Category</h3>
                </div>

                <div class="d-flex align-items-center gap-2">
                    <a href="addProduct.jsp" class="btn-theme-action">
                        <i class="fa-solid fa-plus"></i> Add Bakery Item
                    </a>

                    <select id="sortSelect" onchange="sortProducts()" class="form-select fw-semibold rounded-3 shadow-sm" style="background-color: #ffffff; color: var(--primary-color); border: 1px solid var(--border-color); width: auto;">
                        <option value="default">Sort By: Default</option>
                        <option value="priceLow">Price: Low to High 📈</option>
                        <option value="priceHigh">Price: High to Low 📉</option>
                        <option value="stockHigh">Stock: High to Low 📦</option>
                    </select>

                    <button class="btn px-3 py-2 fw-semibold rounded-3 shadow-sm"
                            style="background-color: var(--secondary-color); color: var(--primary-color); border: 1px solid var(--border-color);"
                            onclick="showAllCategories()">
                        <i class="fa-solid fa-grip me-2"></i> Back to Categories
                    </button>
                </div>
            </div>

            <!-- Product Cards Grid -->
            <div class="row row-cols-1 row-cols-md-2 row-cols-lg-3 g-4" id="productGrid">
                <%
                    if (products != null && !products.isEmpty()) {
                        for (String p : products) {
                            String[] data = p.split(",");

                            String pId = (data.length > 0) ? data[0].trim() : "0";
                            String pName = (data.length > 1) ? data[1].trim() : "Bakery Item";
                            String pCategory = (data.length > 2) ? data[2].trim() : "General";
                            String pPrice = (data.length > 3) ? data[3].trim() : "0";
                            String pStock = (data.length > 4) ? data[4].trim() : "0";
                            String pDesc = (data.length > 5) ? data[5].trim() : "";

                            int stockVal = 0;
                            try { stockVal = Integer.parseInt(pStock); } catch(Exception e){}

                            String imgUrl = "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80";
                            if (data.length > 6 && !data[6].trim().isEmpty()) {
                                imgUrl = data[6].trim();
                            } else if(pCategory.equalsIgnoreCase("Pastry")) {
                                imgUrl = "https://images.unsplash.com/photo-1555507036-ab1f4038808a?auto=format&fit=crop&w=600&q=80";
                            } else if(pCategory.equalsIgnoreCase("Bread")) {
                                imgUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=600&q=80";
                            } else if(pCategory.equalsIgnoreCase("Drinks")) {
                                imgUrl = "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80";
                            }

                            String stockClass = "stock-badge-in";
                            String stockText = stockVal + " in stock";
                            if (stockVal == 0) {
                                stockClass = "stock-badge-out";
                                stockText = "Out of Stock";
                            } else if (stockVal <= 10) {
                                stockClass = "stock-badge-low";
                                stockText = "Low Stock (" + stockVal + ")";
                            }

                           String safeId = pId.replace("'", "\\'");
                               String safeName = pName.replace("'", "\\'");
                               String safeImg = imgUrl.replace("'", "\\'");
                %>
                <div class="col product-card-item" data-category="<%= pCategory.toUpperCase() %>" data-price="<%= pPrice %>" data-stock="<%= pStock %>">
                    <div class="food-card">
                        <div class="food-img-container">
                            <img src="<%= imgUrl %>" class="food-img" alt="<%= pName %>" onerror="this.src='https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80'">
                        </div>
                        <div class="p-4 d-flex flex-column flex-grow-1">
                            <div class="d-flex justify-content-between align-items-start mb-2">
                                <h5 class="fw-bold m-0 item-name" style="color: var(--text-dark);"><%= pName %></h5>
                                <span class="badge rounded-pill px-2 py-1" style="background-color: var(--primary-color); color: var(--secondary-color);"><%= pId %></span>
                            </div>
                            <p class="text-muted small mb-3 flex-grow-1 item-desc"><%= pDesc %></p>

                            <div class="d-flex justify-content-between align-items-center mb-3">
                                <div class="price-tag">Rs. <%= pPrice %></div>
                                <div class="stock-badge-general <%= stockClass %>">
                                    <i class="fa-solid fa-boxes-stacked me-1"></i> <%= stockText %>
                                </div>
                            </div>

                            <!-- DIRECT CLICK TO ADD TO CART -->
                            <div class="click-to-cart-bar mb-2" onclick="addToCart('<%= safeId %>', '<%= safeName %>', <%= pPrice %>, '<%= safeImg %>')">
                                <i class="fa-solid fa-cart-plus me-1"></i> Click to Add to Cart
                            </div>

                            <div class="d-flex gap-2 pt-2 border-top" style="border-color: var(--border-color) !important;">
                                <a href="editProduct.jsp?id=<%= pId %>&name=<%= pName %>&category=<%= pCategory %>&price=<%= pPrice %>&stock=<%= pStock %>&description=<%= pDesc %>&imageUrl=<%= (data.length > 6 ? data[6] : "") %>"
                                   class="btn btn-sm flex-fill py-2" style="background-color: var(--secondary-color); color: var(--primary-color); border: 1px solid var(--border-color); font-weight: 600;">
                                    <i class="fa-regular fa-pen-to-square me-1"></i> Edit
                                </a>
                                <a href="deleteProduct?id=<%= pId %>"
                                   class="btn btn-sm flex-fill py-2" style="background-color: #FFFCF8; color: var(--error-color); border: 1px solid #F3C5C3; font-weight: 600;"
                                   onclick="return confirm('Are you sure you want to delete <%= pName %>?')">
                                    <i class="fa-regular fa-trash-can me-1"></i> Delete
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
                <%
                        }
                    }
                %>
            </div>

            <!-- NO RESULTS FOUND CONTAINER -->
            <div id="noResultsContainer" class="col-12 text-center py-5" style="display: none;">
                <div class="card border-0 shadow-sm p-5 rounded-4" style="background-color: #ffffff; border: 1px solid var(--border-color) !important;">
                    <i class="fa-solid fa-magnifying-glass fa-3x mb-3" style="color: var(--accent-color);"></i>
                    <h4 class="fw-bold" style="color: var(--primary-color);">No Bakery Items Found</h4>
                    <p style="color: var(--text-muted);" id="noResultsText">No matching items found for your search.</p>
                </div>
            </div>
        </div>
    </div>

    <!-- SHOPPING CART OFFCANVAS DRAWER -->
    <div class="offcanvas offcanvas-end rounded-start-4" tabindex="-1" id="cartOffcanvas" style="width: 440px;">
        <div class="offcanvas-header border-bottom" style="background-color: var(--secondary-color);">
            <h5 class="offcanvas-title fw-bold" style="color: var(--primary-color);">
                <i class="fa-solid fa-basket-shopping me-2"></i> Your Bakery Order
            </h5>
            <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Close"></button>
        </div>
        <div class="offcanvas-body d-flex flex-column">
            <div id="cartItemsList" class="flex-grow-1 overflow-auto">
                <!-- Cart Items Rendered Here -->
            </div>

            <div class="border-top pt-3 mt-3">
                <div class="d-flex justify-content-between align-items-center mb-3">
                    <span class="fw-bold text-muted">Total Amount:</span>
                    <span class="fs-4 fw-extrabold" style="color: var(--primary-color);" id="cartTotalAmount">Rs. 0.00</span>
                </div>
                <button class="btn btn-theme-action w-100 py-3 justify-content-center fs-6" onclick="checkoutOrder()">
                    <i class="fa-solid fa-credit-card me-2"></i> Proceed to Checkout
                </button>
            </div>
        </div>
    </div>

<!-- JS Logic -->
    <script>
        let cart = [];
        try {
            let saved = JSON.parse(localStorage.getItem('bakeryCart'));
            if (Array.isArray(saved)) {
                cart = saved.filter(item => item && item.name && item.price);
            }
        } catch(e) {
            cart = [];
        }

        function saveAndRenderCart() {
            localStorage.setItem('bakeryCart', JSON.stringify(cart));
            updateCartBadge();
            renderCartItems();
        }

        function addToCart(id, name, price, img) {
            let itemId = String(id).trim();
            let numPrice = parseFloat(price) || 0;
            let itemName = String(name).trim();
            let itemImg = String(img).trim();

            let existingItem = cart.find(item => String(item.id).trim() === itemId);
            if (existingItem) {
                existingItem.qty = (parseInt(existingItem.qty) || 0) + 1;
            } else {
                cart.push({
                    id: itemId,
                    name: itemName,
                    price: numPrice,
                    img: itemImg,
                    qty: 1
                });
            }
            saveAndRenderCart();

            let badge = document.getElementById('cartBadgeCount');
            if (badge) {
                badge.classList.add('badge-pop');
                setTimeout(() => badge.classList.remove('badge-pop'), 350);
            }

            let cartCanvas = new bootstrap.Offcanvas(document.getElementById('cartOffcanvas'));
            cartCanvas.show();
        }

        function updateCartQty(id, change) {
            let itemId = String(id).trim();
            let itemIndex = cart.findIndex(i => String(i.id).trim() === itemId);
            if (itemIndex !== -1) {
                cart[itemIndex].qty = (parseInt(cart[itemIndex].qty) || 1) + change;
                if (cart[itemIndex].qty <= 0) {
                    cart.splice(itemIndex, 1);
                }
            }
            saveAndRenderCart();
        }

        function removeCartItem(id) {
            let itemId = String(id).trim();
            cart = cart.filter(item => String(item.id).trim() !== itemId);
            saveAndRenderCart();
        }

        function updateCartBadge() {
            let totalQty = cart.reduce((sum, item) => sum + (parseInt(item.qty) || 0), 0);
            let badge = document.getElementById('cartBadgeCount');
            if(badge) badge.innerText = totalQty;
        }

        function renderCartItems() {
            let list = document.getElementById('cartItemsList');
            let totalEl = document.getElementById('cartTotalAmount');

            if (!list || !totalEl) return;

            if (cart.length === 0) {
                list.innerHTML = `
                    <div class="text-center py-5 text-muted">
                        <i class="fa-solid fa-basket-shopping fa-3x mb-3 text-secondary"></i>
                        <p class="m-0 fw-semibold">Your cart is currently empty.</p>
                    </div>`;
                totalEl.innerText = "Rs. 0.00";
                return;
            }

            let html = '';
            let grandTotal = 0;

            cart.forEach(item => {
                let unitPrice = parseFloat(item.price) || 0;
                let itemQty = parseInt(item.qty) || 1;
                let itemTotal = unitPrice * itemQty;
                grandTotal += itemTotal;

                let safeId = String(item.id);

                html += `
                    <div class="d-flex align-items-center gap-3 mb-3 p-3 rounded-3 border bg-white shadow-sm">
                        <img src="${item.img}" style="width: 65px; height: 65px; object-fit: cover; border-radius: 12px;" onerror="this.src='https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80'">
                        <div class="flex-grow-1">
                            <h6 class="fw-bold m-0 text-dark">${item.name}</h6>
                            <small class="text-muted">Rs. ${unitPrice.toFixed(2)} each</small>
                            <div class="fw-bold text-success mt-1">Total: Rs. ${itemTotal.toFixed(2)}</div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button type="button" class="cart-qty-btn" onclick="updateCartQty('${safeId}', -1)">-</button>
                            <span class="fw-extrabold px-1 text-dark fs-6">${itemQty}</span>
                            <button type="button" class="cart-qty-btn" onclick="updateCartQty('${safeId}', 1)">+</button>
                            <button type="button" class="btn-delete-cart-item ms-1" onclick="removeCartItem('${safeId}')" title="Delete Item">
                                <i class="fa-regular fa-trash-can"></i>
                            </button>
                        </div>
                    </div>`;
            });

            list.innerHTML = html;
            totalEl.innerText = 'Rs. ' + grandTotal.toFixed(2);
        }

        function checkoutOrder() {
            if (cart.length === 0) {
                alert('Your cart is empty!');
                return;
            }
            alert('Order placed successfully! Thank you for ordering from Sweet Delights Bakery 🥐🍰');
            cart = [];
            saveAndRenderCart();
            let cartCanvas = bootstrap.Offcanvas.getInstance(document.getElementById('cartOffcanvas'));
            if(cartCanvas) cartCanvas.hide();
        }

        function handleKeyPress(e) {
            if (e.key === 'Enter') performSearch();
        }

        function performSearch() {
            let input = document.getElementById('searchInput').value.toLowerCase().trim();
            let items = document.querySelectorAll('.product-card-item');
            let noResultsBox = document.getElementById('noResultsContainer');
            let visibleCount = 0;

            if (input === '') {
                showAllCategories();
                if (noResultsBox) noResultsBox.style.display = 'none';
                return;
            }

            document.getElementById('categoryViewSection').style.display = 'none';
            document.getElementById('productViewSection').style.display = 'block';
            document.getElementById('selectedCategoryTitle').innerText = 'Search Results for: "' + input + '"';

            items.forEach(item => {
                let cardText = item.innerText.toLowerCase();
                if (cardText.includes(input)) {
                    item.style.display = 'block';
                    visibleCount++;
                } else {
                    item.style.display = 'none';
                }
            });

            if (visibleCount === 0) {
                if (noResultsBox) {
                    noResultsBox.style.display = 'block';
                    document.getElementById('noResultsText').innerText = 'No bakery items found matching "' + input + '". Try another keyword!';
                }
            } else {
                if (noResultsBox) noResultsBox.style.display = 'none';
            }
        }

        function loadCategoryData() {
            let categoryData = JSON.parse(localStorage.getItem('bakeryCategoryData')) || {};

            ['CAKE', 'PASTRY', 'BREAD', 'DRINKS'].forEach(catKey => {
                let data = categoryData[catKey];
                let card = document.getElementById('card-' + catKey);
                let badge = document.getElementById('badge-' + catKey);
                let title = document.getElementById('title-' + catKey);
                let desc = document.getElementById('desc-' + catKey);
                let img = document.getElementById('img-' + catKey);

                if (data) {
                    if (data.title) title.innerText = data.title;
                    if (data.desc) desc.innerText = data.desc;
                    if (data.img) img.src = data.img;

                    if (data.status === 'Unavailable') {
                        if (card) card.classList.add('unavailable-card');
                        if (badge) {
                            badge.className = 'badge bg-danger text-white';
                            badge.innerHTML = '<i class="fa-solid fa-ban me-1"></i> Unavailable';
                        }
                    } else {
                        if (card) card.classList.remove('unavailable-card');
                        if (badge) badge.className = 'badge bg-warning text-dark';
                    }
                }
            });
        }

        function handleCategoryClick(categoryName, titleText) {
            let categoryData = JSON.parse(localStorage.getItem('bakeryCategoryData')) || {};
            let data = categoryData[categoryName];

            if (data && data.status === 'Unavailable') {
                alert('මෙම Category එක දැනට Unavailable (Out of Service) තත්ත්වයේ පවතී!');
                return;
            }
            showProductsOfCategory(categoryName, titleText);
        }

        function showProductsOfCategory(categoryName, titleText) {
            document.getElementById('categoryViewSection').style.display = 'none';
            document.getElementById('productViewSection').style.display = 'block';
            document.getElementById('selectedCategoryTitle').innerText = titleText;
            document.getElementById('searchInput').value = '';

            let noResultsBox = document.getElementById('noResultsContainer');
            if (noResultsBox) noResultsBox.style.display = 'none';

            document.getElementById('sortSelect').value = 'default';

            let items = document.querySelectorAll('.product-card-item');
            items.forEach(item => {
                let cat = item.getAttribute('data-category');
                if (cat === categoryName) {
                    item.style.display = 'block';
                } else {
                    item.style.display = 'none';
                }
            });
        }

        function showAllCategories() {
            document.getElementById('categoryViewSection').style.display = 'block';
            document.getElementById('productViewSection').style.display = 'none';
            let searchInputEl = document.getElementById('searchInput');
            if(searchInputEl) searchInputEl.value = '';
            let noResultsBox = document.getElementById('noResultsContainer');
            if (noResultsBox) noResultsBox.style.display = 'none';
        }

        function sortProducts() {
            let sortValue = document.getElementById('sortSelect').value;
            let grid = document.getElementById('productGrid');
            if(!grid) return;
            let items = Array.from(grid.getElementsByClassName('product-card-item'));

            items.sort((a, b) => {
                let priceA = parseFloat(a.getAttribute('data-price')) || 0;
                let priceB = parseFloat(b.getAttribute('data-price')) || 0;
                let stockA = parseInt(a.getAttribute('data-stock')) || 0;
                let stockB = parseInt(b.getAttribute('data-stock')) || 0;

                if (sortValue === 'priceLow') return priceA - priceB;
                if (sortValue === 'priceHigh') return priceB - priceA;
                if (sortValue === 'stockHigh') return stockB - stockA;
                return 0;
            });

            items.forEach(item => grid.appendChild(item));
        }

        showAllCategories();
        document.addEventListener('DOMContentLoaded', function() {
            loadCategoryData();
            saveAndRenderCart();
        });
    </script>

    <!-- Bootstrap JS -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>