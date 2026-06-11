### download postgresml as docker image and start postgres
### vector store might still be initialized and then we are done after this step
1. execute> run.sh

### initialize postgres with data from users.sql and start postgres client, subsumes step 3 and 4
### can't find path to psql so it fails
2. execute> init.sh

2.1 If container has not started, check with: docker ps -a, 
start postgresml container from docker app:

### connect to postgres db with homebrew client (if step 2 is not executed)
3. /opt/homebrew/bin/psql-17 -U postgresml -h localhost -p 5433 postgresml

### connect to postgres db inside docker container (if step 2 is not executed)
3. docker exec -it f050cce3c4f8 sudo -u postgresml psql -d postgresml

### initialize postgres with data from users.sql (if step 2 is not executed)
4. postgresml=# CREATE ROLE myappuser WITH LOGIN PASSWORD 'mypassword';
   postgresml=# CREATE DATABASE myappdb OWNER myappuser;
   postgresml=# ...etc

### load dog data into postgres
5. start server that will load data in db if data.sql is updated
`scheduler` application (port 8081) needs to be started before loading data (IdeaProjects/ai/jlong/2025-05-16-anthropic/scheduler)
`dummyMessages` end point configured for oauth security, the other end points are unprotected, so auth server is needed for server to start
`auth server` (port 9001) needs to be started before loading data (IdeaProjects/oauth/oauth-server/auth, local-h2 profile)

### sql inside client
6. postgresml=# select * from dog;
7. postgresml=# select count(*) from vector_store;

### list databases
8. oauthdb=# \list

###list all tables in database (here oauthdb)
9. oauthdb=# \dt

### exit client
10. postgresml=# \q

### stop docker container (`docker ps` to find container id)
11. docker stop f050cce3c4f8
