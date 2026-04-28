package org.ejemplo.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class EjercicioMongo {

    public static void main(String[] args) {
        // 1. Configuramos los Codecs para trabajar con objetos Java
        CodecProvider codecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry codecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(codecProvider));

        // 2. Conectamos a nuestro entorno (sustituye con la ruta de tu mongo Atlas o el servidor que utilices)
        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("ecommerce").withCodecRegistry(codecRegistry);
            MongoCollection<ProductoMongo> coleccion = database.getCollection("productos", ProductoMongo.class);

            // 3. INSERCIÓN INICIAL. Si la colección está vacía, cargamos los datos desde el JSON
            if (coleccion.countDocuments() == 0) {
                System.out.println("La colección está vacía. Leyendo JSON...");
                cargarDatosDesdeJson(coleccion);
            } else {
                System.out.println("La base de datos ya contiene " + coleccion.countDocuments() + " productos. Saltando inserción.");
            }

            // 4. INSERCIÓN. Creamos un nuevo producto y lo insertamos
            System.out.println("\n--- INSERTANDO UN PRODUCTO NUEVO ---");
            ProductoMongo nuevoProducto = new ProductoMongo();
            nuevoProducto.setNombre("Ratón Gaming Logitech");
            nuevoProducto.setCategoria("Electrónica");
            nuevoProducto.setPrecio(60.00);
            // Usamos Map.of para crear las especificaciones
            nuevoProducto.setEspecificaciones(Map.of("dpi", "16000", "inalambrico", "Sí"));

            coleccion.insertOne(nuevoProducto);
            System.out.println("Insertado: " + nuevoProducto.getNombre());

            // 5. LEER. Imprimimos los datos del catálogo
            System.out.println("\n--- CATÁLOGO EN MONGODB ---");
            for (ProductoMongo producto : coleccion.find()) {
                System.out.println("- [" + producto.getId() + "] "
                        + producto.getNombre()
                        + " (" + producto.getCategoria() + ")"
                        + " - Precio: " + producto.getPrecio() + "€"
                        + " -> Especificaciones: " + producto.getEspecificaciones());
            }
            System.out.println("\nTotal de productos tras la inserción: " + coleccion.countDocuments());

            // 6. ACTUALIZACIÓN. Actualizamos el precio del producto insertado
            System.out.println("\n--- ACTUALIZANDO UN PRODUCTO ---");
            ProductoMongo productoAActualizar = coleccion.find(eq("nombre", "Ratón Gaming Logitech")).first();

            if (productoAActualizar != null) {
                productoAActualizar.setPrecio(49.99);
                coleccion.replaceOne(eq("nombre", "Ratón Gaming Logitech"), productoAActualizar);
                System.out.println("Precio actualizado para: " + productoAActualizar.getNombre() + " a " + productoAActualizar.getPrecio() + "€");
            }

            // 7. BORRADO. Eliminamos el producto insertado
            System.out.println("\n--- BORRANDO UN PRODUCTO ---");
            coleccion.deleteOne(eq("nombre", "Ratón Gaming Logitech"));
            System.out.println("Producto 'Ratón Gaming Logitech' eliminado del catálogo.");

            // 8. LEER. Comprobamos el número total de productos tras el borrado
            System.out.println("\nTotal de productos tras todas las operaciones: " + coleccion.countDocuments());

            // 9. VACIAR LA COLECCIÓN (Para volver a probar desde cero)
            // System.out.println("\n--- VACIANDO LA BASE DE DATOS ---");
            // coleccion.drop();
            // System.out.println("Colección eliminada. La próxima vez cargará el JSON de nuevo.");
        }
    }

    private static void cargarDatosDesdeJson(MongoCollection<ProductoMongo> coleccion) {
        JsonMapper mapper = new JsonMapper();
        // Leemos el archivo JSON desde la carpeta resources
        try (InputStream inputStream = EjercicioMongo.class.getResourceAsStream("/productos.json")) {
            List<ProductoMongo> productos = mapper.readValue(inputStream, new TypeReference<List<ProductoMongo>>() {});
            coleccion.insertMany(productos);
            System.out.println("¡" + productos.size() + " productos insertados en MongoDB con éxito!");
        } catch (Exception e) {
            System.err.println("Error al leer el JSON: " + e.getMessage());
        }
    }
}
