# ejm-config

Biblioteca para configuração simplificada de serviços remotos via **EJM (Enterprise Java Messaging)** utilizando **RMI** no **Spring Boot**.  
O objetivo da `ejm-config` é permitir que aplicações exponham e consumam serviços remotos de forma segura, desacoplada e padronizada, semelhante ao funcionamento do **EJB Remote**.

---

## 🚀 Instalação

Adicione a dependência da `ejm-config` ao seu projeto (exemplo com **Maven**):

```xml
<dependency>
  <groupId>br.com.ejm</groupId>
  <artifactId>ejm-config</artifactId>
  <version>0.0.1</version>
</dependency>
```
Ou com Gradle:
```
implementation 'br.com.ejm:ejm-config:1.0.0'
```
## 🧩 Estrutura recomendada
É boa prática criar uma biblioteca delegate separada que contenha:
DTOs e Models;
Interfaces de serviço que estendem java.rmi.Remote.
Por exemplo:
```
br.com.usuario.delegate
 ├── dto/
 ├── model/
 └── service/
      └── UsuarioService.java
      
```
Essa biblioteca poderá ser usada tanto no servidor (provider) quanto no cliente (consumer).

## 🖥️ Servidor (Provider)
No projeto que disponibiliza o serviço remoto:

Implemente a interface do delegate:
```
@EjmService(name = "UsuarioService")
public class UsuarioServiceImpl implements UsuarioService {
    // implementação dos métodos
}
```
Habilite o servidor EJM na classe principal:
```
@SpringBootApplication
@EnableEjmServe
public class UsuarioApplication {
    public static void main(String[] args) {
        SpringApplication.run(UsuarioApplication.class, args);
    }
}
```
Garanta que o servidor tenha conectividade de rede acessível para clientes RMI.

## 💻 Cliente (Consumer)
No projeto que consome o serviço remoto:

Habilite o cliente EJM na classe principal:
```
@SpringBootApplication
@EnableEjmClient
public class PedidoApplication {
    public static void main(String[] args) {
        SpringApplication.run(PedidoApplication.class, args);
    }
}
```
Configure o arquivo config-ejm.xml em:

src/main/resources/config-ejm.xml

Exemplo:
```
<ejm>
    <rmi>
        <host>192.168.2.15</host>
        <port>1099</port>
        <base-package>br.com.usersdelegate.remote</base-package>
    </rmi>
</ejm>
```

Injete o serviço remoto normalmente com Spring:
```
@Service
public class PedidoService {

    @Autowired
    private UsuarioService usuarioService;

    public void processar() {
        var usuario = usuarioService.buscarPorId(1L);
        System.out.println(usuario.getNome());
    }
}
```
## ⚙️ Como funciona internamente
A anotação @EnableEjmServe configura automaticamente o registro RMI e expõe os beans anotados com @EjmService.

A anotação @EnableEjmClient lê o arquivo config-ejm.xml e registra dinamicamente os proxies remotos no contexto Spring.
O @Autowired funciona normalmente tanto para beans locais quanto remotos.

## 🧠 Boas práticas
Sempre use uma lib delegate compartilhada entre cliente e servidor.
Mantenha o nome da interface no @EjmService(name = ...) idêntico ao informado no config-ejm.xml.
Evite dependências bidirecionais entre os projetos.

## 📄 Licença
Este projeto está licenciado sob a Licença MIT. Veja o arquivo LICENSE para mais detalhes.