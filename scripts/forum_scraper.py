import os
import re
import time
import sys
from collections import deque
import requests
from bs4 import BeautifulSoup

sys.stdout.reconfigure(encoding='utf-8')
from urllib.parse import urljoin, urlparse, parse_qs, urlencode, urlunparse

# Configurações do Scraper
BASE_URL = 'https://omegas-reader.fmcx.pl/forum/'
OUTPUT_DIR = r'E:\Documentos\Documentos\OMEGAS FORUM\docs\forum_archive_v2'

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36'
}

# Cookies fornecidos originalmente
COOKIES = {
    'phpbb3_wef3p_ct_cookies_test': '%7B%22cookies_names%22%3A%5B%22ct_prev_referer%22%5D%2C%22check_value%22%3A%22a5e6547071a5afa30c11913161052c94%22%7D',
    'phpbb3_wef3p_ct_prev_referer': 'https%3A%2F%2Fomegas-reader.fmcx.pl%2Fforum%2Fviewtopic.php%3Ft%3D15',
    'phpbb3_wef3p_ct_sfw_pass_key': 'c28b015178994be71a1e797cb8bb8e85',
    'phpbb3_wef3p_k': 'v3ytqj2ndoy701kx',
    'phpbb3_wef3p_sid': '7f5e40547bba3fccb032c123a7b1c615',
    'phpbb3_wef3p_u': '1803'
}

def setup_directory():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

session = requests.Session()
session.headers.update(HEADERS)
for name, value in COOKIES.items():
    session.cookies.set(name, value, domain='omegas-reader.fmcx.pl')

def check_login():
    print("Testando autenticação via Cookies...")
    resp = session.get(BASE_URL + 'index.php')
    if 'Wyloguj' in resp.text or 'vitoh200' in resp.text:
        print("Autenticação com cookies validada! VIP Access.")
        return True
        
    print("FALHA CRÍTICA: Os cookies estão expirados. O fórum derrubou a sessão.")
    return False

def clean_url(url):
    parsed = urlparse(url)
    query = parse_qs(parsed.query)
    if 'sid' in query:
        del query['sid']
    new_query = urlencode(query, doseq=True)
    return urlunparse(parsed._replace(query=new_query)).split('#')[0]

def scrape_topic(soup, url):
    parsed = urlparse(url)
    query = parse_qs(parsed.query)
    topic_id = query.get('t', ['unknown'])[0]
    
    title_elem = soup.find('h2', class_='topic-title') or soup.find('h2')
    title = title_elem.text.strip() if title_elem else f"Topic_{topic_id}"
    safe_title = re.sub(r'[^a-zA-Z0-9_\- ]', '', title).replace(' ', '_')
    
    start_param = query.get('start', ['0'])[0]
    page_suffix = f"_p{start_param}" if start_param != '0' else ""
    
    filename = os.path.join(OUTPUT_DIR, f"topic_{topic_id}{page_suffix}_{safe_title}.md")
    
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(f"# {title} (Page: {start_param})\n\n")
        f.write(f"**URL:** {url}\n\n")
        
        posts = soup.find_all('div', class_='postbody')
        for post in posts:
            author_elem = post.find_previous('dl', class_='postprofile')
            author = author_elem.find('a', class_='username').text if author_elem and author_elem.find('a', class_='username') else "Unknown"
            
            content_div = post.find('div', class_='content')
            if content_div:
                content = content_div.get_text(separator='\n', strip=True)
                f.write(f"### Autor: {author}\n")
                f.write(f"{content}\n\n---\n\n")
                
    print(f"Salvo tópico: {filename}")

def main():
    setup_directory()
    if not check_login():
        print("Abortando scraper.")
        # Salva log de falha para monitor ver
        with open("cookie_error.txt", "w") as f:
            f.write("COOKIES EXPIRED")
        return
        
    print("Iniciando spider (crawling) avançado (VIP MODE)...")
    
    queue = deque([BASE_URL + 'index.php'])
    visited = set()
    
    while queue:
        current_url = queue.popleft()
        clean_current = clean_url(current_url)
        
        if clean_current in visited:
            continue
            
        visited.add(clean_current)
        print(f"[{len(visited)} processadas | {len(queue)} na fila] Crawler visitando: {clean_current}")
        
        response = session.get(current_url)
        time.sleep(1)
        
        if response.status_code != 200:
            continue
            
        soup = BeautifulSoup(response.text, 'html.parser')
        
        if 'viewtopic.php' in clean_current:
            scrape_topic(soup, clean_current)
            
        for link in soup.find_all('a', href=True):
            href = link['href']
            full_url = urljoin(current_url, href)
            
            if full_url.startswith(BASE_URL) and ('viewforum.php' in full_url or 'viewtopic.php' in full_url):
                full_url_clean = clean_url(full_url)
                bad_params = ['mark=read', 'hash', 'unwatch', 'watch', 'p=', '&p=', 'mode=', 'view=print', 'view=previous', 'view=next', '&style=']
                if any(bad in full_url_clean for bad in bad_params):
                    continue
                    
                if full_url_clean not in visited and full_url_clean not in queue:
                    queue.append(full_url_clean)
                    
    print(f"Varredura VIP concluída! {len(visited)} páginas únicas analisadas.")

if __name__ == '__main__':
    main()
