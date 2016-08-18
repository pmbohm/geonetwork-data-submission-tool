import subprocess
from django import template

register = template.Library()


@register.simple_tag()
def gitrev():
    try:
        p = subprocess.Popen(["git", "rev-parse", "--short", "HEAD"],
                             stdout=subprocess.PIPE)
        gv = p.communicate()[0]
        return gv.strip()
    except:
        return '(unknown)'
