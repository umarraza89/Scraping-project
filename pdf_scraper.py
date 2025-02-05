import os
import re
import requests
import concurrent.futures
from bs4 import BeautifulSoup

BASE_URL = "https://papers.nips.cc/paper/"
DOWNLOAD_DIR = "mydocuments"
THREAD_POOL_SIZE = 30  # Adjust based on system capability


def create_download_dir():
    """Create download directory if not exists."""
    if not os.path.exists(DOWNLOAD_DIR):
        os.makedirs(DOWNLOAD_DIR)


def get_paper_links(year):
    """Scrapes paper links from the given year."""
    url = f"{BASE_URL}{year}"
    print(f"\nScraping year: {year}")

    response = requests.get(url)
    if response.status_code != 200:
        print(f"Failed to retrieve data for {year}")
        return []

    soup = BeautifulSoup(response.text, 'html.parser')
    paper_elements = soup.select("li.conference a[href], li.none a[href]")

    paper_links = []
    for paper in paper_elements:
        paper_title = paper.text
        paper_url = "https://papers.nips.cc" + paper['href']
        paper_links.append((paper_title, paper_url, year))

    return paper_links


def download_paper(paper_title, paper_url, year):
    """Processes and downloads the PDF of a paper."""
    print(f"\nProcessing: {paper_title}")

    response = requests.get(paper_url)
    if response.status_code != 200:
        print(f"Failed to fetch page: {paper_title}")
        return False

    soup = BeautifulSoup(response.text, 'html.parser')
    pdf_links = soup.select("a[href$='.pdf']")
    
    if not pdf_links:
        print(f"No valid document found for: {paper_title}")
        return False

    for idx, pdf_link in enumerate(pdf_links):
        pdf_url = "https://papers.nips.cc" + pdf_link['href']
        file_suffix = "_1.pdf" if idx == 0 else f"_{idx+1}.pdf"
        download_file(pdf_url, paper_title, file_suffix)

    return True


def download_file(url, title, suffix):
    """Downloads a file from a given URL."""
    safe_title = re.sub(r"[^a-zA-Z0-9.-]", "_", title) + suffix
    file_path = os.path.join(DOWNLOAD_DIR, safe_title)

    response = requests.get(url, stream=True)
    if response.status_code == 200:
        with open(file_path, "wb") as f:
            for chunk in response.iter_content(1024):
                f.write(chunk)
        print(f"Downloaded: {safe_title}")
    else:
        print(f"Failed to download: {safe_title}")


def main():
    create_download_dir()
    year = 2021  # Adjust the year as needed
    paper_links = get_paper_links(year)

    with concurrent.futures.ThreadPoolExecutor(max_workers=THREAD_POOL_SIZE) as executor:
        futures = [executor.submit(download_paper, title, url, year) for title, url, year in paper_links]
        concurrent.futures.wait(futures)

    print("\nAll downloads complete!")


if __name__ == "__main__":
    main()
