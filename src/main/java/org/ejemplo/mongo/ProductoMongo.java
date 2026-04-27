package org.ejemplo.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.Map;

@Data
@NoArgsConstructor
public class ProductoMongo {
    @BsonId
    private ObjectId id;

    private String nombre;
    private String categoria;
    private double precio;

    // Guardamos los atributos dinámicos como un mapa clave-valor
    private Map<String, String> especificaciones;
}
