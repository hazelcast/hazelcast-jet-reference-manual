## Comparison of Jet APIs

Jet provides several APIs which can be used to execute jobs. All APIs
are compiled down to the Core API before being executed. Both pipeline
and java.util.stream APIs are built on top of the Core API.

This chart summarizes the main differences between them:

<table>
	<tr>
		<th style="width: 25%"></th>
		<th style="width: 25%">Pipeline API</th>
		<th style="width: 20%">java.util.stream</th>
		<th style="width: 30%">Core API (DAG)</th>
	</tr>
	<tr>
		<td>Use for</td>
		<td>Building rich bounded and unbounded data pipelines on a variety of sources and sinks.</td>
		<td>Simple transform and reduce operations on top of IMap and IList.</td>		
		<td>
<ul>
<li>Building custom sources and sinks</li>
<li>Integration with other libraries or frameworks</li>
<li>Low-level control over data flow</li>
<li>Fine-tuning performance.</li>
<li>Building DSLs</li>
</ul>
</p>
</td>
	</tr>
	<tr>
		<td>Programming Style</td>
		<td style="text-align: center">Declarative</td>
		<td style="text-align: center">Declarative</td>
		<td style="text-align: center">Imperative</td>
	</tr>
  <tr>
    <td>Works with all sources and sinks</td>
    <td style="text-align: center">✅</td>
    <td style="text-align: center">❌(*)</td>
    <td style="text-align: center">✅</td>
  </tr>
	<tr>
		<td>Transforms (map, flat map, filter)</td>
		<td style="text-align: center">✅</td>
		<td style="text-align: center">✅</td>
		<td style="text-align: center">✅</td>
	</tr>
	<tr>
		<td>Aggregations</td>
		<td style="text-align: center">✅</td>
		<td style="text-align: center">✅(**)</td>
		<td style="text-align: center">✅</td>
	</tr>
	<tr>
		<td>Joins and forks</td>
		<td style="text-align: center">✅</td>
		<td style="text-align: center">❌</td>
		<td style="text-align: center">✅</td>
	</tr>
	<tr>
		<td>Processing bounded data (batch)</td>
		<td style="text-align: center">✅</td>
		<td style="text-align: center">✅</td>
		<td style="text-align: center">✅</td>
	</tr>
	<tr>
		<td>Processing unbounded data (streaming)</td>
		<td style="text-align: center">✅ (***)</td>
		<td style="text-align: center">❌</td>
		<td style="text-align: center">✅</td>
	</tr>
</table>

*: Any source can be used with j.u.stream, but only IMap and IList sinks are supported.<br/>
**: j.u.stream only supports grouping on one input, co-grouping is not supported.
Furthermore aggregation is a terminal operation in and additional transforms can't be applied to aggregation results.<br/>
***: Windowing support will be added in 0.6.
