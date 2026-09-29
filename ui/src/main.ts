import './styles.css'
import { mountSpeciesSearch } from './search'

const root = document.querySelector<HTMLElement>('#app')
if (root) mountSpeciesSearch(root)
