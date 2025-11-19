# ⚙️ ejm-config




Biblioteca para configuração simplificada de serviços remotos via EJM (Enterprise Java Messaging) utilizando RMI no Spring Boot.
Seu objetivo é permitir que aplicações exponham e consumam serviços remotos de forma segura, padronizada e extremamente fácil, funcionando de modo semelhante ao EJB Remote.

### 🧩 Estrutura essencial
```
🔌 Provider (Servidor)
🖧 EJMS + RMI Registry
📦 Cliente (Consumer)
```
### 🧱 Arquitetura — visão geral
```
flowchart LR
A[Client Application<br>Spring Boot + @EnableEjmClient]
--> |RMI Lookup| B((EJM Registry))

    B --> C[EJM Server Application<br>@EnableEjmServe]

    C --> |Exposes Remote Service| D[UsuarioServiceImpl<br>@EjmService]

    A --> |Injects Proxy| D
```
### 📦 Instalação
#### Maven
```
<dependency>
    <groupId>br.com.ejm</groupId>
    <artifactId>ejm-config</artifactId>
    <version>0.0.1-BETA</version>
</dependency>
```
#### Gradle
```
implementation 'br.com.ejm:ejm-config:1.0.0'
```
### 🧩 Estrutura recomendada (Delegate Layer)
Crie uma lib compartilhada contendo DTOs, Models e as interfaces remotas:
```
br.com.usuario.delegate
├── dto/
├── model/
└── service/
└── UsuarioService.java  <-- estende EjmDelegate
```
Essa biblioteca é usada tanto no servidor quanto no cliente.
### 🖥️ Servidor (Provider)
#### Implemente o serviço remoto
```
@EjmService(name = "UsuarioService")
public class UsuarioServiceImpl implements UsuarioService {
// implementação real
}
```
#### Ative o servidor EJM
```
@SpringBootApplication
@EnableEjmServe
public class UsuarioApplication {
public static void main(String[] args) {
SpringApplication.run(UsuarioApplication.class, args);
}
}
```
### 💻 Cliente (Consumer)
#### Ative o EJM Client
```
@SpringBootApplication
@EnableEjmClient
public class PedidoApplication {
public static void main(String[] args) {
SpringApplication.run(PedidoApplication.class, args);
}
}
```
### Configure o config-ejm.xml
```
src/main/resources/config-ejm.xml
```
```
<ejm>
    <rmi>
        <host>192.168.2.15</host>
        <port>1099</port>
        <base-package>br.com.usersdelegate.remote</base-package>
    </rmi>
</ejm>
```
### Consuma o serviço
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
### ⚙️ Funcionamento interno
- @EnableEjmServe cria o RMI Registry e publica automaticamente todos os beans anotados com @EjmService.
- @EnableEjmClient lê o config-ejm.xml e cria os proxies dinâmicos, permitindo @Autowired.
- Toda comunicação é feita via invocação remota RMI.
### ☸️ Compatibilidade com Kubernetes (K8s)
A biblioteca é 100% compatível com Kubernetes.
Para funcionar corretamente no cluster:

1. Exponha o RMI Registry como um Service
2. Garanta que o hostname do provider seja acessível pelo client
3. Utilize Service Discovery interno do K8s (<service-name>.<namespace>.svc.cluster.local)
### 📦 Exemplo Kubernetes — Provider (Servidor)
#### Deployment
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: usuario-provider
spec:
  replicas: 1
  selector:
    matchLabels:
      app: usuario-provider
  template:
    metadata:
      labels:
        app: usuario-provider
    spec:
      containers:
        - name: usuario-provider
          image: seu-registro/usuario-provider:latest
          ports:
            - containerPort: 1099
          env:
            - name: EJM_PORT
              value: "1099"

```
#### Service
```
apiVersion: v1
kind: Service
metadata:
  name: usuario-provider
spec:
  selector:
    app: usuario-provider
  ports:
    - name: rmi
      port: 1099
      targetPort: 1099
  type: ClusterIP

```
### 📦 Exemplo Kubernetes — Client (Consumer)
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pedido-consumer
spec:
  replicas: 1
  selector:
    matchLabels:
      app: pedido-consumer
  template:
    metadata:
      labels:
        app: pedido-consumer
    spec:
      containers:
        - name: pedido-consumer
          image: seu-registro/pedido-consumer:latest
          env:
            - name: EJM_SERVER_HOST
              value: "usuario-provider.default.svc.cluster.local"
            - name: EJM_SERVER_PORT
              value: "1099"

```

### config-ejm.xml para Kubernetes
```
<ejm>
    <rmi>
        <host>${EJM_SERVER_HOST}</host>
        <port>${EJM_SERVER_PORT}</port>
        <base-package>br.com.usersdelegate.remote</base-package>
    </rmi>
</ejm>
```
### 🧠 Boas práticas
- Utilize uma lib delegate compartilhada entre Provider e Consumer
- Mantenha nomes idênticos entre @EjmService(name="...") e o config-ejm.xml
- Evite dependências cruzadas
- Em Kubernetes, sempre conecte usando o service name:
```
usuario-provider.default.svc.cluster.local
```
### 📄 Licença
MIT — veja o arquivo LICENSE.