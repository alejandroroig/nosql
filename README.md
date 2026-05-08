# Operaciones CRUD en NoSQL (MongoDB y DynamoDB)

Este proyecto es una introducción práctica a las bases de datos NoSQL. Implementamos un catálogo de productos realizando operaciones CRUD completas (Crear, Leer, Actualizar, Borrar) en dos de los motores de bases de datos NoSQL más utilizados: **MongoDB** y **Amazon DynamoDB**.

---

## 🚀 1. Entorno de Desarrollo (Docker)

Las bases de datos necesarias para este proyecto se ejecutan, por defecto, de forma aislada dentro de contenedores de **Docker**.

### Cómo levantar el entorno
Abre tu terminal en la carpeta raíz del proyecto (donde se encuentra el archivo `docker-compose.yaml`) y ejecuta:
```bash
docker compose up -d
```
Esto encenderá MongoDB (en el puerto 27017) y un emulador local de DynamoDB (en el puerto 8000).

### Cómo apagar el entorno y comportamiento de los datos
Cuando termines de trabajar, ejecuta:

```bash
docker compose down
```

⚠️ **¡Atención a la persistencia!** Al ejecutar `down`, los contenedores se destruyen. Con nuestra configuración actual, **esto significa que ambas bases de datos se vaciarán por completo**. Al volver a encenderlas al día siguiente, el código Java detectará que están vacías y volverá a cargar automáticamente el archivo `productos.json`.

## 🌐 2. Conexiones (Local vs Nube)
El código viene configurado por defecto para apuntar a tus contenedores Docker locales. Si quieres conectarte a servidores reales en la nube, debes modificar el bloque de conexión de tu código de la siguiente manera:

### Para MongoDB (Ej. MongoDB Atlas)
Cambia la cadena de conexión de `localhost` a tu URI de Mongo Atlas:

```Java
// Cambiar esto:
MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
// Por esto:
MongoClient mongoClient = MongoClients.create("mongodb+srv://tuUsuario:tuPassword@cluster0.abcde.mongodb.net/");
```

### Para Amazon DynamoDB (AWS Academy / Learner Lab)
Si tienes credenciales temporales de AWS, debes hacer dos cambios en EjercicioDynamo.java:

1. Poner tus credenciales reales en la clase `AwsSessionCredentials.create(...)`.

2. Borrar o comentar la línea del Endpoint Local (`endpointOverride`) para que Java salga a internet a buscar tu cuenta de Amazon.

```Java
AwsSessionCredentials credenciales = AwsSessionCredentials.create("TU_ACCESS_KEY", "TU_SECRET", "TU_TOKEN");

DynamoDbClient clienteBase = DynamoDbClient.builder()
// .endpointOverride(URI.create("http://localhost:8000")) <-- ¡ELIMINAR ESTA LÍNEA!
.credentialsProvider(StaticCredentialsProvider.create(credenciales))
.region(Region.US_EAST_1) // Cambia a tu región real
.build();
```

## 🧠 3. Apuntes de Código: Lo que debes entender

Los SDKs (herramientas de conexión) de Java pueden parecer complejos al principio. Aquí te explicamos los conceptos clave que aparecen en el código:

### Codecs en MongoDB (`PojoCodecProvider`)
MongoDB guarda la información en formato BSON, pero nosotros en Java usamos Clases (POJOs). Los "Codecs" actúan como traductores automáticos. Gracias a ellos, no tienes que mapear datos manualmente; le entregas un `ProductoMongo` a la base de datos y ella sola sabe cómo guardarlo.

### El Cliente Mejorado de DynamoDB (`DynamoDbEnhancedClient`)
El cliente base de Amazon te obliga a tratar los datos como diccionarios complicados. Inicializamos el "Enhanced Client", que lee las anotaciones (`@DynamoDbBean`) de tu clase Java y hace el trabajo de traducción por ti, igual que los Codecs en Mongo.

### La Partition Key (Clave Primaria) en DynamoDB
En MongoDB, puedes buscar y borrar un producto por su nombre fácilmente usando `eq("nombre", "Ratón...")`.
Sin embargo, **DynamoDB no te lo permite de forma directa**. Por motivos de rendimiento a escala global, DynamoDB exige estrictamente que le des el **ID (Clave Primaria)** para actualizar (`updateItem`) o borrar (`deleteItem`).

Por eso, en el código de DynamoDB verás que para modificar un artículo hacemos dos pasos:
1. Iteramos la tabla para buscar el producto y averiguar cuál es su UUID secreto autogenerado.
2. Construimos una llave explícita (`Key.builder().partitionValue(...).build()`) con ese ID exacto para poder realizar el borrado o la actualización.

### `find()` vs `scan()`
* En MongoDB usamos `.find()` para leer la colección.
* En DynamoDB usamos `.scan()`.
* **Nota importante:** Un `scan` recorre la tabla de arriba a abajo. Con pocos productos es perfecto, pero en un entorno de producción real en AWS con millones de registros, un `scan` consumiría tu presupuesto y ralentizaría tu aplicación.

### La ventaja de NoSQL: Esquema Flexible (Schema-less)
Si te fijas en el código, el campo `especificaciones` guarda cosas muy distintas: un televisor tiene `pulgadas` y `resolución`, mientras que una camiseta tiene `talla` y `color`.
En una base de datos relacional (como MySQL o PostgreSQL), tendríamos que crear muchas tablas conectadas o dejar columnas vacías para lograr esto. En MongoDB y DynamoDB, al no haber un esquema estricto, cada producto puede tener los atributos que necesite dentro de un mismo documento o ítem. ¡Por eso NoSQL es tan popular en los catálogos de e-commerce!

### Los Identificadores: ObjectId vs UUID
Al imprimir el catálogo por consola, habrás notado que los IDs que se generan automáticamente son muy raros:
* **MongoDB** genera un `ObjectId` (ej. `662d5f...`): Es una cadena hexadecimal de 24 caracteres que contiene, entre otras cosas, una marca de tiempo de cuándo se creó.
* **DynamoDB** usa un `UUID` (ej. `f47ac10b-...`): Es un estándar universal de 36 caracteres diseñado para garantizar que jamás se generen dos IDs iguales en el mundo, algo vital cuando Amazon guarda tus datos repartidos en miles de servidores a la vez.

### La librería Jackson (`JsonMapper`)
Antes de que los datos lleguen a la base de datos, tenemos que leer el archivo `productos.json`. El SDK de Java no sabe leer archivos JSON por sí solo. Para eso usamos la librería externa **Jackson**. Su trabajo es tomar el texto del archivo y convertirlo mágicamente en una lista de objetos Java (`List<ProductoMongo>` o `List<ProductoDynamo>`) que ya podemos enviar a la base de datos.