package org.ejemplo.dynamo;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class EjercicioDynamo {
    static void main(String[] args) {
        // 1. Configuramos las credenciales falsas para el entorno local
        AwsSessionCredentials credenciales = AwsSessionCredentials.create("accessKey", "secretKey",  "sessionToken");

        // 2. Conectamos a nuestro Docker usando el cliente estándar y el mejorado
        try (DynamoDbClient clienteBase = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:8000")) // IMPORTANTE!!! COMENTA ESTA LÍNEA PARA CONECTAR A TU CUENTA DE AWS
                .credentialsProvider(StaticCredentialsProvider.create(credenciales))
                .region(Region.US_EAST_1) // La región no importa en local, pero es obligatoria. En AWS, pon la que corresponda a tu tabla
                .build()) {

            // El cliente mejorado nos permite trabajar con objetos Java directamente, sin tener que mapear manualmente cada campo
            DynamoDbEnhancedClient clienteMejorado = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(clienteBase)
                    .build();

            // Referencia a la tabla
            DynamoDbTable<ProductoDynamo> tabla = clienteMejorado.table("productos", TableSchema.fromBean(ProductoDynamo.class));

            // 3. INSERCIÓN INICIAL. Intentamos crear la tabla. Si ya existe, saltará una excepción y lo ignoramos.
            try {
                tabla.createTable();
                System.out.println("La tabla está vacía (recién creada). Leyendo JSON...");
                cargarDatosDesdeJson(tabla);
            } catch (ResourceInUseException e) {
                System.out.println("La base de datos ya contiene la tabla 'productos'. Saltando inserción inicial.");
            }

            // 4. INSERCIÓN. Creamos un nuevo producto y lo insertamos
            System.out.println("\n--- INSERTANDO UN PRODUCTO NUEVO ---");
            ProductoDynamo nuevoProducto = new ProductoDynamo();
            nuevoProducto.setNombre("Ratón Gaming Logitech");
            nuevoProducto.setCategoria("Electrónica");
            nuevoProducto.setPrecio(60.00);
            // Usamos Map.of para crear las especificaciones
            nuevoProducto.setEspecificaciones(Map.of("dpi", "16000", "inalambrico", "Sí"));

            tabla.putItem(nuevoProducto);
            System.out.println("Insertado: " + nuevoProducto.getNombre());

            // 5. LEER. Imprimimos los datos para comprobar que funciona
            System.out.println("\n--- CATÁLOGO EN DYNAMODB ---");
            tabla.scan().items().forEach(producto ->
                    System.out.println("- [" + producto.getId() + "] "
                            + producto.getNombre()
                            + " (" + producto.getCategoria() + ")"
                            + " - Precio: " + producto.getPrecio() + "€"
                            + " -> Especificaciones: " + producto.getEspecificaciones())
            );

            // DynamoDB no tiene una función rápida equivalente a countDocuments(),
            // así que contamos los elementos que nos devuelve el scan.
            long totalProductos = tabla.scan().items().stream().count();
            System.out.println("\nTotal de productos tras la inserción: " + totalProductos);

            // 6. ACTUALIZACIÓN. DynamoDB quiere el la clave de partición, pero solo tenemos el nombre
            System.out.println("\n--- ACTUALIZANDO UN PRODUCTO ---");

            // Paso 1: Buscar el producto manualmente recorriendo la tabla
            Optional<ProductoDynamo> productoBuscado = tabla.scan().items().stream()
                    .filter(p -> "Ratón Gaming Logitech".equals(p.getNombre()))
                    .findFirst();

            // PASO 2: Si está presente, lo modificamos y actualizamos
            productoBuscado.ifPresentOrElse(
                    p -> {
                        p.setPrecio(49.99);
                        tabla.updateItem(p);
                        System.out.println("Precio actualizado para: " + p.getNombre() + " a " + p.getPrecio() + "€");
                    },
                    () -> System.out.println("No se ha encontrado el producto.")
            );

            // 7. BORRADO. Eliminamos el producto insertado
            System.out.println("\n--- BORRANDO UN PRODUCTO ---");
            // Si el producto existe, ejecutamos el borrado
            productoBuscado.ifPresent(p -> {
                Key claveParaBorrar = Key.builder().partitionValue(p.getId()).build();
                tabla.deleteItem(r -> r.key(claveParaBorrar));
                System.out.println("Producto '" + p.getNombre() + "' eliminado del catálogo.");
            });

            // 8. LEER. Comprobamos el número total de productos tras el borrado
            long totalFinal = tabla.scan().items().stream().count();
            System.out.println("\nTotal de productos tras todas las operaciones: " + totalFinal);

            // 9. VACIAR LA TABLA (Para volver a probar desde cero)
            // System.out.println("\n--- VACIANDO LA BASE DE DATOS ---");
            // tabla.deleteTable();
            // System.out.println("Tabla eliminada. La próxima vez cargará el JSON de nuevo.");
        }
    }

    private static void cargarDatosDesdeJson(DynamoDbTable<ProductoDynamo> tabla) {
        JsonMapper mapper = new JsonMapper();
        // Leemos el archivo JSON desde la carpeta resources
        try (InputStream inputStream = EjercicioDynamo.class.getResourceAsStream("/productos.json")) {
            List<ProductoDynamo> productos = mapper.readValue(inputStream, new TypeReference<List<ProductoDynamo>>() {});

            // El cliente de DynamoDB nos obliga a insertar los elementos uno a uno
            for (ProductoDynamo producto : productos) {
                tabla.putItem(producto);
            }
            System.out.println("¡" + productos.size() + " productos insertados en DynamoDB con éxito!");
        } catch (Exception e) {
            System.err.println("Error al leer el JSON: " + e.getMessage());
        }
    }
}
