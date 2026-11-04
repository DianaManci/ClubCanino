# Club Canino

Tienda de productos para mascotas. La aplicación permite registrarse, iniciar sesión y trabajar según el rol de la cuenta: comprador, vendedor o administrador.

## Roles

- **Comprador:** consulta el catálogo, arma un carrito, confirma un pago simulado y administra el perfil y las mascotas.
- **Vendedor:** gestiona productos, atiende pedidos y edita su perfil.
- **Administrador:** administra usuarios y productos, y consulta el informe de ventas.

El inicio de sesión abre solo el rol asignado a esa cuenta.

## Tecnologías

- Kotlin y layouts XML, sin Jetpack Compose
- Material Components
- Android Gradle Plugin 9.5.0
- `minSdk` 24, `compileSdk` y `targetSdk` 37
- Java 11
- Datos locales en el dispositivo (usuarios, catálogo, pedidos, carritos y mascotas)

No usa un servidor. El pago es una simulación y no pide datos bancarios.

## Cuentas de demostración

La contraseña inicial de las cuentas de demostración y de los usuarios creados por el administrador es `demo123`.

| Correo | Rol |
|---|---|
| ana.gomez@clubcanino.demo | Comprador |
| luis.herrera@clubcanino.demo | Comprador |
| camila.ruiz@clubcanino.demo | Vendedor |
| diego.salas@clubcanino.demo | Vendedor |
| valeria.ortiz@clubcanino.demo | Administrador |

Una cuenta registrada desde la aplicación entra como comprador, con la contraseña que eligió.

## Cómo ejecutarlo

1. Abre el proyecto en Android Studio.
2. Espera a que termine la sincronización de Gradle.
3. Ejecuta la configuración `app` en un emulador o un dispositivo, o genera el APK de depuración:

```
gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
