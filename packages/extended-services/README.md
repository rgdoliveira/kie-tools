## KIE Tooling Extended Services

Powers the DMN Runner and DMN Dev Sandbox features.

## Parameters

- `-p <PORT_NUMBER>`: Sets app port, otherwise it will use config.yaml port.

## Note to Fedora users

### Build

To build this application on Fedora, it's necessary to install some additional packages:

- `sudo dnf install gtk3-devel libappindicator-gtk3-devel`

### Run

To run this application on Fedora, it's necessary to install the App Indicator lib:
Firstly install the following packages:

- `sudo dnf install libappindicator-gtk3`

If you want to have more control over the KIE Tooling Extended Services, it's necessary to enable the App Indicator extension
https://extensions.gnome.org/extension/615/appindicator-support/

## Misc

### Configuration

In the `config.yaml` file you will be able to configure Proxy, Runner and Modeler properties as runner location or modeler URL. Runner ip is `127.0.0.1` and port is a random free port.

### How do I create the image.go?

Firstly install the 2goarray package
`go get github.com/cratonica/2goarray`

Then convert the image to a .go file
`cat icon2.png | /Users/aparedes/go/bin/2goarray Data images > icon.go`

### API

#### `/`

The root route is a proxy and will forward your requests to the autogenerated port

#### `/ping`

[GET] returns the API version, the proxy port and IP and the Modeler URL.

```json
{
  "App": {
    "Version": "0.0.0"
  },
  "Proxy": {
    "IP": "127.0.0.1",
    "Port": 21345
  },
  "Modeler": {
    "Link": "https://kiegroup.github.io/kogito-online/#/"
  }
}
```

### Next Steps

- Limit GraalVM Heap Size