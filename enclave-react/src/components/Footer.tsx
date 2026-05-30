export default function Footer(){
  return (
    <footer className="site-footer">
      <div className="container">
        <div className="footer-left">
          <img src="/enclave.png" alt="Enclave logo" width={40} height={40} />
          <div>
            <strong>Enclave</strong>
            <div>© {new Date().getFullYear()} Saif Mukhtar</div>
          </div>
        </div>
        <div className="footer-links">
          <a href="https://github.com/saifmukhtar/enclave" target="_blank" rel="noopener noreferrer">Source</a>
          <a href="https://github.com/saifmukhtar" target="_blank" rel="noopener noreferrer">Author</a>
        </div>
      </div>
    </footer>
  )
}
