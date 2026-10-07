import UIKit
import WebKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
  var window: UIWindow?

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    let window = UIWindow(frame: UIScreen.main.bounds)
    window.rootViewController = FixtureViewController()
    window.makeKeyAndVisible()
    self.window = window
    return true
  }
}

final class FixtureViewController: UIViewController, WKNavigationDelegate {
  private let webView = WKWebView()
  private let webLoadStatus = UILabel()

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .systemBackground

    let result = UILabel()
    result.text = "Ready"
    result.accessibilityIdentifier = "native-result"

    let action = UIButton(type: .system)
    action.setTitle("Change native state", for: .normal)
    action.accessibilityIdentifier = "native-action"
    action.addAction(UIAction { _ in result.text = "Native changed" }, for: .touchUpInside)

    webLoadStatus.text = "Web loading"
    webLoadStatus.accessibilityIdentifier = "web-load-status"
    webView.accessibilityIdentifier = "hybrid-webview"
    webView.navigationDelegate = self
    webView.isInspectable = true
    webView.loadHTMLString(Self.page, baseURL: URL(string: "http://127.0.0.1"))

    let stack = UIStackView(arrangedSubviews: [result, action, webLoadStatus, webView])
    stack.axis = .vertical
    stack.spacing = 12
    stack.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(stack)
    NSLayoutConstraint.activate([
      stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
      stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
      stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
      stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12)
    ])
  }

  func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
    webLoadStatus.text = "Web loaded"
  }

  func webView(
    _ webView: WKWebView,
    didFail navigation: WKNavigation!,
    withError error: Error
  ) {
    webLoadStatus.text = "Web load failed"
  }

  func webView(
    _ webView: WKWebView,
    didFailProvisionalNavigation navigation: WKNavigation!,
    withError error: Error
  ) {
    webLoadStatus.text = "Web load failed"
  }

  private static let page = """
    <!doctype html><html><body>
      <button id="web-action" onclick="document.getElementById('web-result').textContent='Web changed'">Change web state</button>
      <p id="web-result">Web ready</p>
    </body></html>
    """
}
