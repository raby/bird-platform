import './styles.css'
import { mountApp } from './app'

const root = document.querySelector<HTMLElement>('#app')
if (root) mountApp(root)
