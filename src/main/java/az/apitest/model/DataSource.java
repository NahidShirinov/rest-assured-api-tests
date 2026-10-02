package az.apitest.model;

/**
 * Suite daxilində baza bağlantısı (config-dəki db.* əvəzinə):
 *
 *   "datasources": { "orders": { "url": "jdbc:postgresql://localhost:5432/shop", "user": "app", "password": "${env.DB_PASS}" } }
 */
public class DataSource {
    public String url;
    public String user;
    public String password;
}
