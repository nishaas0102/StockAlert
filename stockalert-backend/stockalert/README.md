# StockAlert Backend

Java 17 | Spring Boot 3.3.5 | Maven | MySQL | springdoc-openapi 2.6.0

## Run
1. Start MySQL. Edit username/password in `src/main/resources/application.properties`.
   (The database `stockalert_db` is created automatically.)
2. Build:  `mvn clean install`   (Windows and Linux/Mac use the same command)
3. Run:    `mvn spring-boot:run`
4. Swagger UI: http://localhost:8080/swagger-ui.html

## API
Products
- POST   /api/products
- GET    /api/products?keyword=&category=&lowStockOnly=false&page=0&size=10&sortBy=id&sortDir=asc
- GET    /api/products/{id}
- PUT    /api/products/{id}
- DELETE /api/products/{id}

Stock movements (type = IN or OUT)
- POST /api/stock-movements
- GET  /api/stock-movements?productId=&page=0&size=10

Reorder alerts
- GET /api/reorder-alerts?resolved=false&page=0&size=10
- PUT /api/reorder-alerts/{id}/resolve

Alerts are created automatically when quantity <= reorderLevel and
auto-resolved when stock rises above the reorder level.

## Quick test
POST /api/products
{"name":"USB Cable","sku":"USB-001","category":"Accessories","quantity":20,"reorderLevel":5,"price":199.00}

POST /api/stock-movements
{"productId":1,"type":"OUT","quantity":16,"note":"Sold"}   -> creates a reorder alert
