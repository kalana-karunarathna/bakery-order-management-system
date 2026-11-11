<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Add Bakery Item - Sweet Delights</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">

    <style>
        :root {
            --primary-color: #6B4226;
            --secondary-color: #FFF8F0;
            --accent-color: #D9A066;
            --bg-color: #FFFCF8;
            --text-dark: #2D2926;
            --border-color: #E8DDD1;
        }

        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-dark);
        }

        .form-card {
            border: 1px solid var(--border-color);
            border-radius: 20px;
            background: #ffffff;
            box-shadow: 0 10px 30px rgba(107, 66, 38, 0.08);
        }

        .btn-submit {
            background-color: var(--primary-color);
            color: var(--secondary-color);
            font-weight: 700;
            border-radius: 12px;
            padding: 12px;
            border: none;
            transition: all 0.3s;
        }

        .btn-submit:hover {
            background-color: #52321C;
            color: #ffffff;
        }

        .form-control, .form-select {
            border-color: var(--border-color);
        }

        .form-control:focus, .form-select:focus {
            border-color: var(--accent-color);
            box-shadow: 0 0 0 0.25rem rgba(217, 160, 102, 0.25);
        }
    </style>
</head>
<body class="py-5">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-md-6">
                <div class="card form-card p-4">
                    <div class="d-flex align-items-center mb-4">
                        <a href="products" class="btn rounded-circle me-3" style="background-color: var(--secondary-color); color: var(--primary-color); border: 1px solid var(--border-color);"><i class="fa-solid fa-arrow-left"></i></a>
                        <h4 class="fw-bold m-0" style="color: var(--primary-color);">Add Item to Category</h4>
                    </div>

                    <form action="addProduct" method="post">
                        <div class="mb-3">
                            <label class="form-label fw-semibold">Product ID</label>
                            <input type="text" name="id" class="form-control form-control-lg rounded-3" placeholder="e.g. P005" required>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-semibold">Product Name</label>
                            <input type="text" name="name" class="form-control form-control-lg rounded-3" placeholder="e.g. Chocolate Donut / Iced Coffee" required>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-semibold">Select Target Category</label>
                            <select name="category" class="form-select form-select-lg rounded-3" required>
                                <option value="Cake">Cake 🍰</option>
                                <option value="Pastry">Pastry 🥐</option>
                                <option value="Bread">Bread 🍞</option>
                                <option value="Drinks">Drinks 🧃</option>
                            </select>

                        </div>
                        <div class="row mb-3">
                            <div class="col-6">
                                <label class="form-label fw-semibold">Price (Rs.)</label>
                                <input type="number" step="0.01" name="price" class="form-control form-control-lg rounded-3" placeholder="250.00" required>
                            </div>
                            <div class="col-6">
                                <label class="form-label fw-semibold">Stock Quantity</label>
                                <input type="number" name="stock" class="form-control form-control-lg rounded-3" placeholder="15" required>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label fw-semibold">Image URL (Optional)</label>
                            <input type="text" name="imageUrl" class="form-control form-control-lg rounded-3" placeholder="https://example.com/photo.jpg">
                        </div>
                        <div class="mb-4">
                            <label class="form-label fw-semibold">Description</label>
                            <textarea name="description" class="form-control rounded-3" rows="3" placeholder="Write a brief description..." ></textarea>
                        </div>
                        <button type="submit" class="btn btn-submit w-100">Save Item to Category</button>
                    </form>
                </div>
            </div>
        </div>
    </div>
</body>
</html>