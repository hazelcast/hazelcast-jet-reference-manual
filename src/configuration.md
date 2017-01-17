# Configuration

## Programmatic Configuration

## XML Configuration

## List of Options

<table>
    <tr>
      <th>Name</th>
      <th>Description</th>
      <th>Default Value</th>
    </tr>
    <tr>
        <td>Cooperative Thread Count</td>
        <td>
            Maximum number of cooperative threads to be used for execution of jobs.
        </td>
        <td>`Runtime.getRuntime().availableProcessors()`</td>
    </tr>
    <tr>
        <td>Temp Directory</td>
        <td>
            Directory where temporary files will be placed, such as JAR files 
            submitted by clients.
        </td>
        <td>Jet will create a temp directory, which will be deleted on exit.</td>
    </tr>
    <tr>
        <td>Flow Control Period</td>
        <td>
            While executing a Jet job there is the issue of regulating the rate at
            which one member of the cluster sends data to another member. The
            receiver will regularly report to each sender how much more data it
            is allowed to send over a given DAG edge. This option sets the 
            length (in milliseconds) of the interval between flow-control 
            packets.
        </td>
        <td>100ms</td>
    </tr>
    <tr>
        <td>Edge Defaults</td>
        <td>
            The default values to be used for all edges.
        </td>
        <td>[See Per Edge Configuration Options](#tuning-edges)</td>
    </tr>    
</table>
