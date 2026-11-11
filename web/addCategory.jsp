<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sweet Delights Bakery - Add Category</title>

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
        }

        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: var(--bg-color);
            background-image:
                radial-gradient(at 0% 0%, rgba(217, 160, 102, 0.1) 0px, transparent 50%),
                radial-gradient(at 100% 100%, rgba(107, 66, 38, 0.08) 0px, transparent 50%);
            background-attachment: fixed;
            color: var(--text-dark);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 40px 0;
        }

        .form-card {
            background: #ffffff;
            border: 1px solid var(--border-color);
            border-radius: 24px;
            box-shadow: 0 15px 35px rgba(107, 66, 38, 0.1);
            overflow: hidden;
            max-width: 550px;
            width: 100%;
        }

        .form-header {
            background-color: var(--secondary-color);
            border-bottom: 1px solid var(--border-color);
            padding: 25px 30px;
            text-align: center;
        }

        .form-header h2 {
            font-family: 'Playfair Display', serif;
            font-weight: 800;
            color: var(--primary-color);
            margin: 0;
        }

        .form-body {
            padding: 35px 30px;
        }

        .form-label {
            font-weight: 700;
            color: var(--text-dark);
            margin-bottom: 8px;
        }

        .form-control, .form-select {
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 12px 16px;
            font-size: 1rem;
            background-color: #FAF4EE50;
            color: var(--text-dark);
            transition: all 0.3s ease;
        }

        .form-control:focus, .form-select:focus {
            border-color: var(--accent-color);
            box-shadow: 0 0 0 4px rgba(217, 160, 102, 0.15);
            background-color: #ffffff;
        }

        .btn-submit {
            background-color: var(--primary-color);
            color: var(--secondary-color);
            font-weight: 700;
            padding: 12px 25px;
            border-radius: 12px;
            border: none;
            width: 100%;
            transition: all 0.3s ease;
        }

        .btn-submit:hover {
            background-color: #52321C;
            color: #ffffff;
            transform: translateY(-2px);
            box-shadow: 0 8px 20px rgba(107, 66, 38, 0.2);
        }

        .btn-back {
            background-color: transparent;
            color: var(--text-muted);
            font-weight: 600;
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 10px 20px;
            width: 100%;
            text-decoration: none;
            display: inline-block;
            text-align: center;
            margin-top: 10px;
            transition: all 0.2s;
        }

        .btn-back:hover {
            background-color: var(--secondary-color);
            color: var(--primary-color);
        }
    </style>
</head>
<body>

    <div class="container">
        <div class="row justify-content-center">
            <div class="col-md-12">
                <div class="form-card">
                    <div class="form-header">
                        <h2><i class="fa-solid fa-layer-group me-2"></i> Add New Category</h2>
                        <p class="text-muted small m-0 mt-1">Configure category specific details</p>
                    </div>

                    <div class="form-body">
                        <form action="addCategory" method="POST">

                            <!-- Category Name -->
                            <div class="mb-4">
                                <label for="categoryName" class="form-label">
                                    <i class="fa-solid fa-tag me-1 text-muted"></i> Category Name
                                </label>
                                <input type="text" class="form-control" id="categoryName" name="categoryName" placeholder="e.g. Cakes, Pastries, Breads" required>
                            </div>

                            <!-- Category Description -->
                            <div class="mb-4">
                                <label for="categoryDescription" class="form-label">
                                    <i class="fa-solid fa-align-left me-1 text-muted"></i> Description
                                </label>
                                <textarea class="form-control" id="categoryDescription" name="categoryDescription" rows="3" placeholder="Write a short description about this category..." required></textarea>
                            </div>

                            <!-- Category Image URL -->
                            <div class="mb-4">
                                <label for="categoryImageUrl" class="form-label">
                                    <i class="fa-solid fa-image me-1 text-muted"></i> Category Image URL
                                </label>
                                <input type="url" class="form-control" id="categoryImageUrl" name="categoryImageUrl" placeholder="https://images.unsplash.com/photo-..." required>
                            </div>

                            <!-- Status -->
                            <div class="mb-4">
                                <label for="categoryStatus" class="form-label">
                                    <i class="fa-solid fa-circle-check me-1 text-muted"></i> Status
                                </label>
                                <select class="form-select" id="categoryStatus" name="categoryStatus">
                                    <option value="Available">Available (Active)</option>
                                    <option value="Unavailable">Unavailable (Out of Service)</option>
                                </select>
                            </div>

                            <button type="submit" class="btn btn-submit mb-2">
                                <i class="fa-solid fa-plus-circle me-1"></i> Save Category
                            </button>
                            <a href="productList.jsp" class="btn-back">
                                <i class="fa-solid fa-arrow-left me-1"></i> Back to Menu
                            </a>

                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Bootstrap JS -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>