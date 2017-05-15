By default the processor receives items from all inbound edges as they
arrive. However, there are important cases where the reception of one
edge must be delayed until all other edges are consumed in full. A major
example is a "hash join": to be able to process the data stream, a processor must first load some state, for example a lookup table. This can be modeled as a join of two or more data streams where all but one have a small amount of data. These small streams can be consumed in full in preparation to process the main stream.

Edge consumption order is controlled by the **priority** property. Edges
are sorted by their priority number (ascending) and consumed in that
order. Edges with the same priority are consumed without particular
ordering (as the data arrives).
