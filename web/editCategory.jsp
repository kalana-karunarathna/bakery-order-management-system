<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Edit Category - Sweet Delights</title>
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

        .btn-update {
            background-color: var(--primary-color);
            color: var(--secondary-color);
            font-weight: 700;
            border-radius: 12px;
            padding: 12px;
            border: none;
            transition: all 0.3s;
        }

        .btn-update:hover {
            background-color: #52321C;
            color: #ffffff;
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
                        <h4 class="fw-bold m-0" style="color: var(--primary-color);">Edit Category Details</h4>
                    </div>

                    <form onsubmit="saveCategoryChanges(event)">
                        <input type="hidden" id="categoryKey" value="${param.name != null ? param.name.toUpperCase() : 'CAKE'}">

                        <div class="mb-3">
                            <label class="form-label fw-semibold">Category Display Title</label>
                            <input type="text" id="categoryTitle" class="form-control form-control-lg rounded-3" value="${param.name} Collection" required>
                        </div>

                        <div class="mb-3">
                            <label class="form-label fw-semibold">Description</label>
                            <input type="text" id="categoryDesc" class="form-control form-control-lg rounded-3" placeholder="e.g. Delicious, custom & fresh cakes" required>
                        </div>

                        <div class="mb-3">
                            <label class="form-label fw-semibold">Image URL (Photo)</label>
                            <input type="text" id="categoryImg" class="form-control form-control-lg rounded-3" placeholder="https://images.unsplash.com/photo-...">
                        </div>

                        <div class="mb-4">
                            <label class="form-label fw-semibold">Availability Status</label>
                            <select id="categoryStatus" class="form-select form-select-lg rounded-3">
                                <option value="Available">Available ✅</option>
                                <option value="Unavailable">Unavailable / Out of Service ❌</option>
                            </select>
                        </div>

                        <button type="submit" class="btn btn-update w-100">Save Category Changes</button>
                    </form>
                </div>
            </div>
        </div>
    </div>

    <script>
        // Load existing values from LocalStorage if available
        document.addEventListener('DOMContentLoaded', function() {
            let key = document.getElementById('categoryKey').value;
            let categoryData = JSON.parse(localStorage.getItem('bakeryCategoryData')) || {};

            if (categoryData[key]) {
                if (categoryData[key].title) document.getElementById('categoryTitle').value = categoryData[key].title;
                if (categoryData[key].desc) document.getElementById('categoryDesc').value = categoryData[key].desc;
                if (categoryData[key].img) document.getElementById('categoryImg').value = categoryData[key].img;
                if (categoryData[key].status) document.getElementById('categoryStatus').value = categoryData[key].status;
            }
        });

        function saveCategoryChanges(e) {
            e.preventDefault();
            let key = document.getElementById('categoryKey').value;
            let categoryData = JSON.parse(localStorage.getItem('bakeryCategoryData')) || {};

            categoryData[key] = {
                title: document.getElementById('categoryTitle').value,
                desc: document.getElementById('categoryDesc').value,
                img: document.getElementById('categoryImg').value,
                status: document.getElementById('categoryStatus').value
            };

            localStorage.setItem('bakeryCategoryData', JSON.stringify(categoryData));
            window.location.href = 'products';
        }
    </script>
</body>
</html>