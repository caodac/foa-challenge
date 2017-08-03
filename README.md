Translator FOA Challenge
========================

To build this code, you'll need to have [sbt](http://www.scala-sbt.org)
version 0.13.13 or greater.

```
sbt run
```

Then visit http://localhost:9000/challenge. By default you will have an
empty database. To register new user, simply ```POST``` to the endpoint
[http://localhost:9000/challenge](http://localhost:9000/challenge)
with a JSON payload:

```json
{
   "email": "foobar@ncats.io",
   "firstname": "foo",
   "lastname": "bar"
}
```

If all goes well, a unique id is returned that you can use. At any point,
this resource lists all registered users:
[http://localhost:9000/challenge/@list](http://localhost:9000/challenge/@list).
To simulate user advancing to the next stage, you can use the ```@next```
[http://localhost:9000/challenge/0399a2b0-8db6-4aed-8cb7-03a79a7de22a/@next](http://localhost:9000/challenge/0399a2b0-8db6-4aed-8cb7-03a79a7de22a/@next).
