By default the processor receives items from all inbound edges as they
arrive. However, there are important cases where the reception of one
edge must be delayed until all other edges are consumed in full. A major
example is a join operation. Collating items from several edges by a
common key implies buffering the data from all edges except one before
emitting any results. Often there is one edge with much more data than
the others and this one does not need to be buffered if all the other
data is ready.

Edge consumption order is controlled by the **priority** property. Edges
are sorted by their priority number (ascending) and consumed in that
order. Edges with the same priority are consumed without particular
ordering (as the data arrives).