This is the software implementation of JikesRVM Java Try-Catch work-stealing framework (TryCatchWS). This software contains the following (each directory has its own readme file for clarity and usage):

1) Patch files against Jikes RVM to supported Try-Catch work-stealing,
2) Few simple Java testcases to demonstrate the usage,
3) JastAdd compiler jar file to inject Try-Catch code blocks to support work-stealing.


You may use this software for your research. This software is still evolving at The Australian National University. Whenever there is any enhancement or bug fixes, this download page will be updated. As a courtesy to the developers, please cite the following papers while using this software for publications:

BibTex:

@Inproceedings{KumarWS2012,
   author = {Kumar, Vivek and Frampton, Daniel and Blackburn, Stephen M. and Grove, David and Tardieu, Olivier},
   title = {Work-stealing Without the Baggage},
   booktitle = {Proceedings of the ACM International Conference on Object Oriented Programming Systems Languages and Applications},
   series = {OOPSLA '12},
   year = {2012},
   location = {Tucson, Arizona, USA},
   pages = {297--314},
   doi = {10.1145/2384616.2384639},
   publisher = {ACM},
} 

@Inproceedings{KumarWS2014,
   author = {Kumar, Vivek and Blackburn, Stephen M. and Grove, David},
   title = {Friendly Barriers: Efficient Work-stealing with Return Barriers},
   booktitle = {Proceedings of the 10th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments},
   series = {VEE '14},
   year = {2014},
   location = {Salt Lake City, Utah, USA},
   pages = {165--176},
   doi = {10.1145/2576195.2576207},
   publisher = {ACM},
} 

Or:

V. Kumar, D. Frampton, S. M. Blackburn, D. Grove, and O. Tardieu. Work-stealing without the baggage. In Proceedings of the ACM International Conference on Object Oriented Programming Systems Languages and Applications, OOPSLA '12, pages 297–314, New York, NY, USA, 2012.

V. Kumar, S. M. Blackburn, and D. Grove. Friendly barriers: Efficient work-stealing with return barriers. In Proceedings of the 10th ACM SIGPLAN/SIGOPS International Conference on Virtual Execution Environments, VEE '14, pages 165–176, New York, NY, USA, 2014.

