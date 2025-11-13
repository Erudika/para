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

In the `./helm/` directory of this repo, execute the following console command:

```console
$ helm install para ./para --set appconfigVolume.path=$(pwd)/application.conf
```

The command deploys Para on the Kubernetes cluster in the default configuration. The [configuration](#configuration) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `para` deployment:

```console
$ helm uninstall para
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

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
| `appconfigVolume.path`              | Absolute path to a local `application.conf` file              | `/etc/para/application.conf`                             |
| `persistentVolumes.data.size`       | Requested capacity for `/para/data` PVC                       | `5Gi`                                                    |
| `persistentVolumes.data.accessModes`| Access modes for `/para/data` PVC                             | `[ReadWriteOnce]`                                        |
| `persistentVolumes.data.storageClassName` | StorageClass for `/para/data` PVC                        | `""`                                                     |
| `persistentVolumes.lib.size`        | Requested capacity for `/para/lib` PVC                        | `1Gi`                                                    |
| `persistentVolumes.lib.accessModes` | Access modes for `/para/lib` PVC                              | `[ReadWriteOnce]`                                        |
| `persistentVolumes.lib.storageClassName` | StorageClass for `/para/lib` PVC                       | `""`                                                     |
| `javaOpts`                          | `JAVA_OPTS` JVM arguments                                     | `-Xmx512m -Xms512m -Dconfig.file=/para/config/application.conf` |
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
| `ecrHelper.enabled`                 | Enable the optional ECR credential helper                     | `false`                                                  |

For more information please refer to the [Para README](https://github.com/Erudika/para/blob/master/README.md).

A YAML file that specifies the values for the above parameters can be provided while installing the chart. For example,

```console
$ helm install para ./para -f values.yaml
```

> **Tip**: You can use the default [values.yaml](values.yaml)
