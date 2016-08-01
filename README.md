# Functional Requirements

    - a simple web crawler which given a URL , should output a site map, showing the static assets for each page 
    - It should be limited to one domain
    - (ASSUMED) base url is the fundamental unit of parallelism/constraints, not a domain - this is because 
                many large domains are multi-DC and different sections can be potentially crawled concurrently
    - (ASSUMED) non-invasive: i.e. for any given domain it should have a configurable sleep with default 10 seconds 
                between crawling max 3 pages concurrently
 
# Non-Functional Requirements
    
    It should meet all and excel in at least one of the following areas
    
    - robustness -> handle imperfect html, good error handling 
    - performance -> concurrent: multiple domains can be crawled in parallel so that the system can scale to real world
    - code structure & layout -> extendible: no need for parametrized url extraction but extendible for various extractor implementations
     

# Design Decisions and Challenges

    My main design goal was to achieve highly concurrent crawler and at the same time respect non-invasive nature for 
    each target domain/base url. Using Futures for http communication and allowing base urls to be defined as targets
    allows the crawler to utilise all available local resources, that is network, processor and memory when it is asked
    to crawl many different target domains, subdomains, or base urls. 

    Other design decisions:
    
    - both url and content hash of each Page are used for deduplication of effort
    - using html cleaner to achieve robustness in handling real-world html which is often not perfect xml structure 
    - using dispatch futures for concurrent http requests because http requests can spend a lot of time waiting
    - demonstration of functional style of testing properties with random generators as well besides common unit-tests 
    
    Not implemented but potentially interesting:
    - extractor is a simple trait-class hierarchy but it could be refactored into a more sophisticated plugin 
        architecture where multiple extractors could be applied by configuration. URLSs are often 
        parametrized/non-trivial, e.g. paginated, and often there is a custom extractor required for various sites.
    - in a more comprehensive implementation, configuration object would be need to represent various settings
        which could be global with per-target overrides 
    
# Anatomy

        +---------------------------------------------------------------------------+
        |                               MAIN PROGRAM                                |
        | ON STARTUP CHECKS FOR ALL .crawl FILE AND REBUILDS A CRAWLER STATE FOR EACH|
        | EACH NEW QUERY IS LOOKED UP IN /logs                                      |
        |   ..IF A .tmp FILE EXISTS FOR THE TASK DISPLAY ALREADY IN PROGRESS        |
        |   ..IF A .txt FILE EXISTS AND IS NOT TOO OLD USE IT AS CACHED RESULT      |
        +----------+----------------------------+--------------------------------+--+
                   |                            |                                |
                   |                            |                                |
                   v                +-----------v-----------+    /src/..         |
                CRAWLER             |  TASK extends Thread  |    /bin/..         |
            +-------------+         |                       |        /crawler    |
            |             |         +-create(TARGET: URL) { |        /crawler.bat|
        +---v--+      +---+---+     |  ID=hash(TARGET)      |    /log/..         v
        | SCAN |      | CRAWL |     |  FILE=./log/<ID>.tmp  |        /334k5jkjh.crawl
        | PAGE +------> LINKS |     |  fput(FILE, TARGET) <--------+ /jh345k5hk.crawl
        |      |      |       |     |  fread(FILE, DATA)    |      +>/kj23455jj.crawl
        ++-----+      +-------+     |}                      |      | /...
         |                          +-run() {               |      |              
         |  +-------------+         |  new CRAWLER(TARGET)  |      |              
         +-> RESULT STREAM +---------->STREAM<Page>()       |      |              
            +-------------+         |  for(PAGE<-STREAM) {  |      |              
                                    |    print(PAGE)        |      |              
                                    |    fput(FILE, PAGE) +--------+                  
                                    |  }                    |                    
                                    |}                      |
                                    |                       |
                                    +-----------------------+




# Operations (UNIX)

    ./gradlew build
    ./build/scripts/crawler <target-1> [<target-2>, [...,target-n]]
    
# Operations (WINDOWS)

    ./gradlew.bat build
    ./build/scripts/crawler.bat <target-1> [<target-2>, [...,target-n]]

