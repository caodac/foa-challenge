import json
import random
import urllib2

alpha = ['b','c','d','f','g','h','j','k','l','n','p','q','x','y','z','w','v']
url = 'http://localhost:9000/challenge'
headers = {
    'Content-Type': 'application/json'
}

def randstr(len):
    s = ''
    for p in range(1,len+1):
        s += random.choice(alpha)
    return s

def register():
    user = {'email': randstr(random.randint(5,10))
            + '@' +randstr(6)+random.choice(['.com','.net',
                                             '.org','.gov',
                                             '.us','.io']),
            'name': randstr(10),
            'answer': random.choice(['',randstr(18),'caesarvenividivici'])
    }
    payload = json.dumps(user, indent=4, separators=(',',': '))
    req = urllib2.Request(url, payload, headers)
    res = urllib2.urlopen(req)
    print res.read()

    
if __name__ == "__main__":
    import sys
    count = 1
    if len(sys.argv) > 1:
        count = int(sys.argv[1])
    for i in range(1, count+1):
        register()
