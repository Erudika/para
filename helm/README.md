# Para Helm Chart for Kubernetes

[Para](https://para.com) is a multi-tenant backend service for busy developers.

## Introduction

This chart bootstraps a [Para](https://github.com/Erudika/para) deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.

This chart installs Para in a K8s cluster. We also offer a fully managed Para service at [ParaIO.com](https://paraio.com)

## Prerequisites

- Helm 3.0+
- Kubernetes 1.21+ (for the optional CronJob helper)

## Quick Start

**Note:** The Para configuration file is mounted as a volume from the local filesystem to the pod.
When Para starts up for the first time, it saves the root app keys to that file inside the pod at `/para/config/application.conf`.
You can override the secret key for the root app with `rootSecretOverride` (see `values.yaml` for details)

In the `./helm/` directory of this repo, execute the following console command:

```console
$ helm install para ./para
```

The command deploys Para on the Kubernetes cluster in the default configuration. The [configuration](#configuration) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `para` deployment:

```console
$ helm uninstall para
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Running Para with plugins

Optionally, you can add one or more plugins to Para before the K8s cluster is initialized, using `initContainers`.
Set `downloadInitContainer.enabled: true` and edit the command `downloadInitContainer.command`. The command is supposed to
download all additional plugins and JDBC drivers and put them in the `/para/lib` folder inside the pod. For example, you can
configure Para to use the SQL plugin and connect to a PostgreSQL server like this:

**`values.yaml`**
```yaml
downloadInitContainer:
  command: "wget https://jdbc.postgresql.org/download/postgresql-42.7.7.jar -O /para/lib/postgresql-jdbc.jar && wget https://repo1.maven.org/maven2/com/erudika/para-dao-sql/1.49.1/para-dao-sql-1.49.1-shaded.jar -O /para/lib/para-dao-sql.jar"
  enabled: true
```

**`application.conf`**
```ini
para.dao = "SqlDAO"
para.sql.driver = "org.postgresql.Driver"
para.sql.url = "postgresql://localhost:5432/para"
para.sql.user = "postgres"
para.sql.password = "mysecretpassword"
```

## Root secret override

If you prefer to inject predetermined secret key for the root Para app `app:para`
for initialization on the first run, set `rootSecretOverride` with the exact Secret 
payload you want rendered. The chart creates a Secret named `para-root-secret` by
default, or you can provide `rootSecretOverride.name` as the Secret name.

```yaml
rootSecretOverride:
  name: para-root-app-secret
  type: Opaque
  stringData:
    paraSecret: "custom_secret"
```

## Configuration

The following table lists the configurable parameters of the Para chart and their default values.

| Parameter                           | Description                                                   | Default                                                  |
|-------------------------------------|---------------------------------------------------------------|----------------------------------------------------------|
| `image.repository`                  | Para image name                                               | `erudikaltd/para`                                        |
| `image.tag`                         | Para image tag                                                | `latest_stable`                                          |
| `image.pullPolicy`                  | Image pull policy                                             | `IfNotPresent`                                           |
| `image.pullSecrets`                 | References to image pull secrets                              | `[]`                                                     |
| `service.type`                      | Kubernetes Service type                                       | `ClusterIP`                                              |
| `service.port`                      | Service HTTP port                                             | `8080`                                                   |
| `service.name`                      | Service port name                                             | `http`                                                   |
| `applicationConf`                   | Para configuration block (config map)                         | Example Para config in `values.yaml`                     |
| `rootSecretOverride`                | Optional root secret override, saved as Secret in the cluster | `{}`                                                     |
| `persistentVolumes.data.size`       | Requested capacity for `/para/data` PVC                       | `5Gi`                                                    |
| `persistentVolumes.data.accessModes`| Access modes for `/para/data` PVC                             | `[ReadWriteOnce]`                                        |
| `persistentVolumes.data.storageClassName` | StorageClass for `/para/data` PVC                       | `""`                                                     |
| `persistentVolumes.lib.size`        | Requested capacity for `/para/lib` PVC                        | `1Gi`                                                    |
| `persistentVolumes.lib.accessModes` | Access modes for `/para/lib` PVC                              | `[ReadWriteOnce]`                                        |
| `persistentVolumes.lib.storageClassName` | StorageClass for `/para/lib` PVC                         | `""`                                                     |
| `downloadInitContainer.enabled`     | Run a command to fetch plugin dependencies into `/para/lib`   | `false`                                                  |
| `downloadInitContainer.command`     | Shell snippet (wget/curl) executed by the init container      | `wget https://jdbc.org/driver.jar -O /para/lib/jdbc.jar` |
| `javaOpts`                          | `JAVA_OPTS` JVM arguments                                     | `-Xmx512m -Xms512m -Dpara.port=8080 -Dloader.path=/para/lib -Dconfig.file=/para/config/application.conf` |
| `podAnnotations`                    | Pod annotations                                               | `{}`                                                     |
| `extraEnvs`                         | Extra environment variables                                   | `[]`                                                     |
| `updateStrategy`                    | Deployment update strategy                                    | `RollingUpdate`                                          |
| `ingress.enabled`                   | Create Ingress                                                | `false`                                                  |
| `ingress.className`                 | Ingress class name                                            | `""`                                                     |
| `ingress.hosts[0].host`             | Hostname for the Ingress                                      | `para.local`                                             |
| `ingress.hosts[0].paths[0].path`    | HTTP path served by the Ingress                               | `/`                                                      |
| `ingress.tls`                       | TLS configuration                                             | `[]`                                                     |
| `resources`                         | CPU/Memory resource requests/limits                           | `{}`                                                     |
| `nodeSelector`                      | Node selector                                                 | `{}`                                                     |
| `tolerations`                       | Tolerations                                                   | `[]`                                                     |
| `affinity`                          | Affinity rules                                                | `{}`                                                     |

For more information please refer to the [Para README](https://github.com/Erudika/para/blob/master/README.md).

A YAML file that specifies the values for the above parameters can be provided while installing the chart. For example,

```console
$ helm install para ./para -f values.yaml
```

> **Tip**: You can use the default [values.yaml](values.yaml)
