# Quickstart: Panel de reparto del gasto de grupo (024)

## 1. Preparación

```bash
docker compose up --build
```

No hay migraciones. Hace falta un grupo con 6 miembros, con al menos un perfil simulado, un nombre de varias palabras y otra moneda disponible (por ejemplo USD).

## 2. Validación manual

Hacer los pasos 1 a 7 en modo claro y en modo oscuro.

1. **Disposición**: abrir "Nuevo gasto" con más de 820px: datos a la izquierda y panel a la derecha. Achicar la ventana: el panel pasa abajo.
2. **Interruptor**: arranca encendido. Apagarlo: aparece la nota de gasto personal y se puede guardar sin participantes.
3. **Partes iguales**: cargar $100 con 3 personas tildadas: $33,34 para quien pagó y $33,33 para el resto, con la nota del centavo.
4. **Porcentajes**: mover sliders y escribir valores con decimales. Cada fila muestra su monto mientras se escribe. El indicador dice cuánto falta y se pone verde al llegar a 100.
5. **Repartir lo que falta**: escribir 50 en una persona y tocar el botón: el resto queda repartido y se puede guardar.
6. **Personalizado**: escribir importes; el indicador dice cuánto falta o sobra. Destildar a alguien: su campo desaparece.
7. **Otra moneda**: elegir USD con una fecha pasada: cada fila muestra el monto en USD y "≈" en la moneda del grupo.
8. **Monto grande**: con $300.000 en Porcentajes, los sliders miden lo mismo en todas las filas y no se achican.
9. **Nombres**: cada fila muestra solo el nombre de pila; el tooltip, el completo. Quien pagó tiene el ícono de billete.
10. **Celular**: a 360px no hay scroll horizontal y en cada fila el nombre va arriba y el control abajo.
11. **Guardar**: guardar un gasto por cada método y comprobar en el detalle que las partes coinciden con las que mostraba el panel.

## 3. Regresión

Gastos de grupo y edición de gasto personal (017, 022 T077), cotización (022 T070), selector de pagador (022 T073).
