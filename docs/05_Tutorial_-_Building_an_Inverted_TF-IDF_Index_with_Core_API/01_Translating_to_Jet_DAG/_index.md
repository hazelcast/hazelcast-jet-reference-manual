The code given so far is a great option as long as your dataset is small
enough to not require scaling out to a cluster. There are some caveats
due to the details of how the JDK's Fork/Join engine parallelizes the
work, but when done right it will perform very well. The concerns of
_scaling out_, however, have a significant impact on the shape of the
computation. While in single-node concurrent computing a major challenge
is sharing mutable data, in multi-node distributed computing the major
challenge is simply sharing --- of any kind.