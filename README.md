# Akka-apps

## Run app locally

```bash
$ sbt -Dconfig.file=src/main/resources/dev.conf run
```

## Run app in kubernetes

```
$ sbt docker:publishLocal
$ kubectl apply -f deployment/akka-service.yml
$ kubectl port-forward service/chatsystem 8080:8080
```

Then open browser with http://localhost:8080

## Testing

```bash
$ sbt test multi-jvm:test
```
