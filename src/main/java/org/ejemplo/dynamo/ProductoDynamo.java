package org.ejemplo.dynamo;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@DynamoDbBean // Etiqueta obligatoria para el Enhanced Client de Dynamo
public class ProductoDynamo {
    private String id = UUID.randomUUID().toString(); // Auto-generamos un ID aleatorio por defecto

    private String nombre;
    private String categoria;
    private double precio;

    private Map<String, String> especificaciones;

    @DynamoDbPartitionKey // DynamoDB necesita saber cuál es la clave principal
    public String getId() {
        return id;
    }
}
