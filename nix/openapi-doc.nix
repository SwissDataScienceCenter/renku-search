{ runCommand, writeText, swagger-ui }:
let
  indexhtml = writeText "index.html" ''
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <meta name="description" content="SwaggerUI" />
      <title>SwaggerUI</title>
      <link rel="stylesheet" href="/swagger-ui.css" />
    </head>
    <body>
        <div id="swagger-ui"></div>
        <script src="/swagger-ui-bundle.js" crossorigin></script>
        <script src="/swagger-ui-standalone-preset.js" crossorigin></script>
        <script>
         window.onload = () => {
             window.ui = SwaggerUIBundle({
                 url: 'http://localhost:8080/search/spec.json',
                 dom_id: '#swagger-ui',
                 presets: [
                     SwaggerUIBundle.presets.apis,
                     SwaggerUIStandalonePreset
                 ]
             });
         };
        </script>
    </body>
    </html>
  '';
in
runCommand "openapi-docs" {} ''
  mkdir $out
  ln -snf ${swagger-ui}/lib/node_modules/swagger-ui/dist/* $out/
  ln -snf ${indexhtml} $out/index.html
''
