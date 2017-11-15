Hazelcast Jet defines several kinds of API, each of which can be used to
build computation jobs. However, there are significant differences between them.

The most fundamental API is the Core API. All the other APIs are
different front ends to it. It establishes strong low-level abstractions
that expose all the mechanics of Jet's computation. While it can
definitely be used to build a Jet job "from scratch", it is quite
unwieldy to do so because for many aspects there is only one right way
to set them up and many wrong ones.

This is where the high-level APIs come in: they give you less control,
but allow you to express your business logic in a far more concise and
readable form. Your first choice to implement a Jet job should be the Pipeline API. It is powerful and expressive, yet quite simple to grasp and become productive in. 

The second choice is Jet's implementation of the java.util.stream API, which some users already familiar with the JDK implementation may find appealing. However, it is less expressive and also suffers from a paradigm mismatch: it promises to deliver the job's result in the return value, which is a cumbersome way to interact with a distributed computation system. The return value's scope is restricted to the scope of the Java variable that holds it, but the actual results remain in the Jet cluster, leading even to possible memory leaks if not handled with
care.

This chart summarizes the main differences between the APIs.

<table>
	<tr>
		<th style="width: 25%"></th>
		<th style="width: 25%">Pipeline API</th>
		<th style="width: 20%">java.util.stream</th>
		<th style="width: 30%">Core API (DAG)</th>
	</tr>
	<tr>
		<td>Use cases</td>
		<td>Build rich data pipelines on a variety of sources and sinks.</td>
		<td>Simple transform and reduce operations on top of IMap and IList.</td>		
		<td>
<ul>
<li>Implement a custom source or sink</li>
<li>Integrate with other libraries or frameworks</li>
<li>Low-level control over data flow</li>
<li>Fine-tune performance</li>
<li>Build a DSL</li>
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
