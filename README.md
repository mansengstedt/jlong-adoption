# 2025-05-16-anthropic
https://spring.io/blog/2025/05/20/your-first-spring-ai-1

<!--
- you first AI application with SPring Ai 1.0
- spring ai is ga! get the bits in the usual space: start.spring.io
- we are in a unique space in the java community. 90% of what people talk about when they talk about Ai engineering is just integration with models, most of which have REST endpiints 
- this is just integration and nothing does integration better than the java and spring communities. 
- we build the apps whose logic drives the organizations and that guard the data that fuel the apps.
-->

- we are in an enviable position because the best place to introdued these new AI integrations is right here, in these spring-based systems and services. 
- and spring ai is the easiest way to do that. 
- in this article were going to look at the BRAND new Spring Ai 1.0 and look at how it delivers on some of the things you'll have come to expect from a Spring project, including Spring Boot starters, portable service abstractions, an elegant component model and Java DSL, gfood support for observability, production-worthiness, and more.
- spring ai provides abstractions over common types of models supporting things like image generation, audio transcription, embeddings, and - most commonly - chat. when you build an application i nthe AI space, you need to assemble models to serve different parts of the whole piece. the many choices are a feature, noit a bug. 
- for our application, well use the world-class Anthropic Claude chat model. spring ai supports countles models, but for this application Claude is a naturally great fit.  
- Claude was designed with the HHH design philosophy: it is helpful, harmless, and honest. as a result, claude is more often polite, stable, and conservative in uncertain or sensitive contexts. this makes it a _very_ good choice for enterprise applications,btw! claude's also perfect at document comprehension and following multi-step instructions. 
- when you talk to a chat model, you send it a human language string caled a *user prompt*.
- to get started u wil need an aPI KEY. get one from the anthroopic developer portal.
- anthripicx provides the chat model, which is arguablty te msot important part, but we also need a SQL database to store the dogs. and we need a way to create embeddings out of the data in our SQL database. those embeddings are naturally searched in a vector store. the good news is, PostgreSQL is capable of doign _all_ through: it can be a SQL database (of course), a  `VectorStore` implementation (thanks to the `vector` extensions), _and_ an `EmbeddingModel` (thanks to the `postgresml` extensions). 
- i have two scripts in the `db` folder. u will need docker to run them. run the first one, called `run.sh`. this will launch postgres as a docker image on y our machine with the extensinos preloaded. then run `init.sh`, which will install the user and credential that our application will se. 
- hit the spring initializr, choose PgVector, Web, Actuator, GraalVM, JDBC Chat Memory, Data JDBC, and PostgresML. 
- make sure to add the `spring-a-advisors-vector-store`
- i chose java 24 and apavhe maven. maybe you choose gradle and kotlin or groovy. neither of us should ever choose a creakingly old java 17!
- for the applicatino to work well need to specify some configuration: the anthropic key and the relevant databasee stuff. specify the sql files to load. datasource connectivity bits. etc.
- lets look at how to do that with chatclient: tell it ur name and then askfor an aswer.
- uh oh! introduce the concept of an advisor. advisors are how we post- and pre-process requests intedned for the chat model. this is the QuestionAnswerAdvisor. it in turn requires some memory.
- well store that memory in PostgreSQL. configure a `JdbcChatMemoryAdvisor`. try again. it works! (who'da thunk?)
- now lets build something more involved.  
- were going to build an app that supports adopting dogs at a shelter. why dogs? cuz therye awesome. even the terrible ones. i have a terible dog. that dog reminds me of another dog about which i learned during the pandemic named Prancer. 
- lets build an assistant ot help with questions : CODE showing the basic assistnat controler 
- we wnat people to be able to ask uestions about dogs. the problem is that right now, the http endpoin is  a naked pass throught o our underlying model. people coudl do their history homework on it! this is hardly what we want. no, instead, we want the model to be focused on a mission. we need to give it a prime directiive, of sorts, in terms of which it attempts to answer user prompts. this prime directive is called a *system prompt*. 
- CODE for system prompt 
- try it out: 'do yo have any neurotic dogs?'
- it should resond that it doesnt have any in the Pooch Palace location. its acting ike an employee of our ficticious dog adoption agency! 
- lets give it access to our data. the data is in the SQL DB. we dont want to send all of it. we _could_, in this case, as there are only 18 records and claude supports an enormous token count. 
- whats  a token count? its a proxy for the cost - both dollars-and-cents and complexity - of handling a given requst to a model. the higher the tokens spent for a given request, the more expensive! 
- its a good thing to keep an eye on the token costs. make sure youre keeping an eye on the actuator/metrics integration showing the gen_ai token count. 
- the actuato rmetrics are poweted by micrometer. you can send to any timeseries db you want. 
- so we dont want to blow through our token budget. one way to do that is to send only thrw requsts that might be germaine to the query at hand. well store the data in a *vector store*, which supports smenatic similarity search. in this case were using pgvector store b ut of course spring ai supports countless others. the problem is, you dont put raw data in a vector store. you put, well, _vectors_, sometimes called embeddings. somebody ahs to take raw data  - text, images, audio, whatever - and turn it into an embedding. well use an *embedding model* to do that. again, sprign ai supports countless options here, btu fro this demo well use `postgresml`. 
- this will allow us to only select that which is germaine to the rquest ands end it. 
- well need touse another advisor - `QuestionAnswerAdvisor`  - for this. 
- CODE: shwo the vector store ingest loop and note that we wrapped it in a test so it doesn't executve over and over 
- try it out: 'do you have any neurotic dogs?'
- great. its owrking. and well,too. the natural next step would be to adopt this doggo. lets do so. we want the model to be able to interact with our proprietary scheduling algorithm. well build out a `Tool` which we can use here. 
- demo: try it locally
- this is all wel and good but of course well want to reuse this logic. 
- introduction to MCP. its actually an anthropic thing. nice! we love it so muhc we raced to crate a great jav sdk for it. if u use the java skd, u r using the spring ai created implementation.
- lets extract that as a separate service called scheduler. it works! 
- u know whats really awesome though? here,w ere using the chatclient and the chat client is talking to claude. but even though we created this app to use claude, it doesnt have to be claude that u use when u do this. this protocol is open. 
- if, however, u r using claude desktop, then ur in luck. you can reuse the same scheduling service directly from claude desktop too with the new support in their MAX plan for remote mcp services. heres how. 
- lets extract this service out. its running on port :8080/sse. it has to be a public service. one handy trick to get a public URl for your app is to use `ngrok` which is a utility i pay like $8 a month for. use it  like this: `ngrok http 8080`. itll dump u out and give u a URL. go to your claude desktop settings, click on profile, rhn add a remote integration (show screenshots here). now you can run the entire process end-to-end - ask about neurotic dogs and then try to adopt them - from ur claude desktop chat.
- production worthy Ai is key! 
- graalvm 
- virtual threads 
- a good, scalable platform like cloudfoundry or kubernetes. 

