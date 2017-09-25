Translator FOA Challenge
========================

This repository contains source code to the [NCATS Translator Challenge](https://grants.nih.gov/grants/guide/notice-files/NOT-TR-17-023.html). *The challenge is now closed*, but you can still browse a running instance [here](https://ncats.io/challenge/aae8d287-0eb3-410d-a652-b166f83b747b).

Building the Code
=================

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
   "name": "Team awesome",
   "answer": "answer to the puzzle"
}
```

The wiki page provides some guidance on solving the challenge:

https://spotlite.nih.gov/translator/foa-challenge/wikis/home
